/*
 * Copyright 2016 Scalalaz Podcast Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.scalalaz.gen

import java.io.{ BufferedWriter, File, FileWriter }

import scalatags.Text.TypedTag
import scalatags.Text.all._

trait Rss {
  import fastparse.all._

  case class RssTag(name: String, content: String)
  case class RssTags(tags: Seq[RssTag])

  // parsers
  val ws           = P(" ".rep)
  val value        = P(CharIn('a' to 'z', "_", "-").rep(1).!)
  val tagParameter = P("tag=")
  val start        = P("@:rssTagStart" ~ ws ~ tagParameter ~ value ~ ":")
  val end          = P("@:rssTagEnd" ~ ws ~ tagParameter ~ value ~ ".")
  val content =
    P(start ~ (!end ~ AnyChar).rep(1).! ~ end).filter(v => v._1 == v._3)
  val rssTag   = content.map(v => RssTag(v._1, v._2))
  val markdown = ((!rssTag ~ AnyChar).rep ~ rssTag.!).rep

  /**
    * It doing parsing and xml tag generation from [[RssTag]]s.
    * TODO: xml tag generations should be handled at different function, after content is parsed.
    * @param filename
    * @param lines Markdown with rssTagsStart/End markup
    * @return rss tags if it founded at markdown
    */
  def parseRssTags(filename: String, lines: String): Option[TypedTag[String]] = {
    val parsed = markdown.parse(lines)

    val r = parsed match {
      case Parsed.Success(tagSeq, _) =>
        val rssTags: Seq[RssTag] = tagSeq.map { t =>
          val p                      = rssTag.parse(t)
          val Parsed.Success(tag, _) = p
          tag
        }

        if (rssTags.exists(_.name.length > 0))
          Option(makeRssItemTags(filename, rssTags))
        else
          None

      case f: Parsed.Failure =>
        println(s"Parsed.Failure: ${ f.extra.traced.trace }")
        None
    }
    r
  }

  def getListOfMdFiles(dir: String): List[File] = {
    val files: List[File] = new java.io.File(dir).listFiles.toList
    files.filter(_.getName.endsWith(".md"))
  }

  def makeRssItemTags(filename: String, tags: Seq[RssTag]): TypedTag[String] = {
    val xmlItem = tag("item")(tags.map { t =>
                                tag(t.name)(t.content)
                              },
                              tag("guid")(attr("isPermaLink") := "false")(
                                  s"http://scalalaz.ru/$filename"
                              ))
    xmlItem
  }

  /**
    * It forming final RSS xml
    * @param rssItems rss items tags
    * @return
    */
  def BuildRssXml(rssItems: List[TypedTag[String]]): TypedTag[String] = {
    tag("rss")(
        attr("version") := "2.0",
        attr("xmlns:itunes") := "http://www.w3.org/2005/Atom",
        attr("xmlns:atom") := "http://www.itunes.com/dtds/podcast-1.0.dtd"
    )(
        tag("channel")(tag("title")("Scalalaz Podcast"),
                       tag("description")(
                           "Подкаст о программировании на языке Scala (16+)"
                       ),
                       tag("link")("http://scalalaz.ru"),
                       tag("language")("ru-RU"),
                       rssItems)
    )
  }

  def parseFiles(files: List[File]): List[TypedTag[String]] = {
    val parsed = for (f <- files) yield {
      val source = scala.io.Source.fromFile(f.getAbsolutePath)
      val lines = try {
        Option(source.mkString)
      } finally {
        source.close()
        None
      }

      // replacing `md` extention with `html`
      val filename = f.getName.split('.').init ++ Seq("html") mkString "."

      // actual parsing
      lines.flatMap {
        parseRssTags(filename, _)
      }
    }

    val xmlResult = parsed.filter(_.isDefined).map(_.get)
    xmlResult
  }
}

object Rss extends Rss {

  def apply(inPath: String, outPath: String) = {
    val files: List[File] = getListOfMdFiles(inPath)

    val parsed  = parseFiles(files) // parsing
    val xml     = BuildRssXml(parsed) // xml generation
    val xmlHead = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"

    // writing
    val file = new File(s"$outPath/rss.xml")
    val bw   = new BufferedWriter(new FileWriter(file))
    bw.write(xmlHead + xml.toString())
    bw.close()
  }

}
