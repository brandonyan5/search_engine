package sol

import src.{FileIO, PorterStemmer, StopWords}

import java.io.IOException
import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.matching.Regex
import scala.xml.{Node, NodeSeq}

/**
 * Provides an XML indexer, produces files for a querier
 *
 * @param inputFile - the filename of the XML wiki to be indexed
 */
class Indexer(val inputFile: String) {

  /**
   * Stored hashmap of the titles of the pages
   */
  val titles: mutable.HashMap[Int, String] = mutable.HashMap()

  /**
   * Stored hashmap of all unique words in the corpus which map to another
   * hashmap that maps page id to how many times
   * the word appears in the document. After relevanceCalc is run, the inner
   * map goes from doc id to doc-word relevance
   */
  val allWords:
    mutable.HashMap[String, mutable.HashMap[Int, Double]] = mutable.HashMap()

  /**
   * Stores hashmap of doc id to the names of all pages it links to
   */
  val allLinks: mutable.HashMap[Int, List[String]] = mutable.HashMap()

  /**
   * Stores the pageRank algorithm results
   */
  protected var pageRankings: mutable.HashMap[Int, Double] = mutable.HashMap()


  /**
   * Given a pageNode, it will put the title inside the titles hashmap and add
   * their processed versions to a map
   *
   * @param page the pageNode we are trying to parse the title from
   */
  def titleParse(page: Node): Option[String] = {
    val title: String = (page \ "title").text // title of the page
    val trimTitle = title.trim
    val idd: String = (page \ "id").text // the id of the page
    val id: Int = idd.trim.toInt
    titles.put(id, trimTitle)
  }

  /**
   * makes a list of all the words in a page to iterate on depending on the
   * input string
   *
   * @param pageContents the whole string we want to split into words
   * @param inputRegex   the regex we want to to split the string into
   * @return a list of all the words we want to match on later
   */
  def tokenizer(pageContents: String, inputRegex: String): List[String] = {
    val regex = new Regex(inputRegex)
    val matchIterate = regex.findAllMatchIn(pageContents)
    val matchesList = matchIterate.toList.map { aMatch => aMatch.matched }
    matchesList
  }

  /**
   * returns a boolean indicating whether a string is a link in our xml file
   *
   * @param inputText the string we are checking
   * @return true if it is a link, false if it is not
   */
  def linkMatcher(inputText: String): Boolean = {
    inputText.matches("""\[\[[^\[]+?\]\]""")
  }

  /**
   * splits a link into its left and right parts
   *
   * @param middleString the middle part separating the left and right
   * @param endString    the place we want to stop searching over the string
   * @param splitString  the string you are trying to split
   * @return a tuple with the the left part and right part of the
   *         original string
   */
  def linkSplitter(middleString: String, endString: String,
                   splitString: String): (String, String) = {
    val barIndex: Int = splitString.indexOf(middleString)
    val endIndex: Int = splitString.indexOf(endString)

    val leftPart: String = splitString
      .subSequence(2, barIndex).asInstanceOf[String]
    val rightPart: String = splitString
      .subSequence(barIndex + 1, endIndex).asInstanceOf[String]

    (leftPart, rightPart)
  }

  /**
   * adds the link id to the arrayBuffer of the page that has the link
   *
   * @param currId the id of the current page
   * @param title  the title we are trying to add to the pages list of links
   */
  def allLinkAdd(currId: Int, title: String): Unit = {
    val trimmedWord = title.trim
    if (allLinks.contains(currId)) {
      var ourList: List[String] = allLinks(currId)
      // make sure we only put title into links list once time
      if (!ourList.contains(trimmedWord)) {
        ourList = trimmedWord :: ourList
        allLinks.put(currId, ourList)
      }
    } else { // if the current page doesn't have a list of links yet
      if (!title.equals(titles(currId))) {
        var ourList: List[String] = List()
        ourList = trimmedWord :: ourList
        allLinks.put(currId, ourList)
      }
    }
  }

  /**
   * processes a whole page of data and puts it into the hashmap inputted
   * into the helper
   *
   * @param page  the page that we want to process
   * @param table the table we are wanting to update with the information
   *              in the page
   */
  def pageProcessor(page: Node, table: mutable.HashMap[String,
    mutable.HashMap[Int, Double]]): Unit = {
    val idd: String = (page \ "id").text
    val id: Int = idd.trim.toInt
    val title: String = (page \ "title").text // titles of the xml file
    val textBulk: String = (page \ "text").text // text of the xml file
    // concatenate titles and text to process both at same time
    val textAndTitles: String = title + " " + textBulk
    val textList = tokenizer(textAndTitles,
      """\[\[[^\[]+?\]\]|[^\W_]+'[^\W_]+|[^\W_]+""")
    // the list of words we want to iterate on

    // recur over the list putting things in the hashmap
    @tailrec
    def listRecur(lst: List[String]): Unit = lst match {
      case Nil =>
      case head :: tail =>
        if (!StopWords.isStopWord(head)) { // check for stop word, if not stem word
          val stemmedWord = PorterStemmer.stem(head)
          val lowerWord = stemmedWord.toLowerCase()

          // if the head is a link, we then match to see what type of link it is
          if (linkMatcher(head)) {
            if (head.matches(""".*[|].*""")) {
              val bothSide: (String, String) =
                linkSplitter("|", "]", head) // tuple of split str
              allLinkAdd(id, bothSide._1)
              val matchesList: List[String] = tokenizer(bothSide._2,
                """[^\W_]+'[^\W_]+|[^\W_]+""")
              listRecur(matchesList ::: tail) // adding right part into the allWords doc

            } else if (head.matches(""".*[:].*""")) {
              val bothSide: (String, String) = linkSplitter(":",
                "]", head)
              val allList: List[String] =
                tokenizer(bothSide._1 + " " + bothSide._2,
                  """[^\W_]+'[^\W_]+|[^\W_]+""")
              allLinkAdd(id, allList.mkString(" "))
              listRecur(allList ::: tail)

            } else {
              val matchesList: List[String] = tokenizer(head,
                """[^\W_]+'[^\W_]+|[^\W_]+""")
              val title: String = matchesList.mkString(" ")
              allLinkAdd(id, title)
              listRecur(matchesList ::: tail)

            }
          } else if (!table.contains(lowerWord)) { // if the table does not yet contain this word,
            val docHash = new mutable.HashMap[Int, Double]() // create new id -> hashmap for our new word
            docHash.put(id, 1) // add the current doc id and initialize count of word as 1
            table.put(lowerWord, docHash) // put the stemmed word with its corresponding hashmap into the field
            listRecur(tail)
          } else if (!table(lowerWord).contains(id)) { // if we are on a new document but word was in previous doc
            val docHash: mutable.HashMap[Int, Double] = table(lowerWord)
            docHash.put(id, 1)
            listRecur(tail)
          } else { // if the word is in the corpus and was in current document
            val currWordCount: Double = table(lowerWord)(id) // retrieve current count of word
            table(lowerWord).put(id, currWordCount + 1) // increment count of current word
            listRecur(tail)
          }
        } else {
          listRecur(tail)
        }
    }

    listRecur(textList)
  }

  /**
   * finalProcessor takes in the xml File and processes all the pages; titles,
   * links, and unique words are separated
   */
  def finalProcessor(): Unit = {
    val wholeFile: Node = xml.XML.loadFile(inputFile)
    var allPages: NodeSeq = wholeFile \ "page"
    while (allPages.nonEmpty) {
      titleParse(allPages.head)
      pageProcessor(allPages.head, allWords)
      allPages = allPages.tail
    }
  }

  /**
   * find the word that appears the most for a doc with an id equal to the
   * index
   *
   * @param table the hashmap we are searching over
   * @param index the doc id we are searching over
   * @return the word that appears the most inside the doc index
   */
  def maxFinder(table: mutable.HashMap[String,
    mutable.HashMap[Int, Double]], index: Int): Double = {
    var emp: List[Double] = List()
    for ((_, v) <- table) {
      if (v.contains(index)) {
        v(index) :: emp
        emp = v(index) :: emp
      }
    }
    emp.max
  }

  /**
   * maps doc ids to the highest number of times a unique word appears in
   * the doc
   *
   * @param table the initial hashmap we are searching over
   * @return a hashmap with doc ids -> highest number of times a word appears
   */
  def maxStore(table: mutable.HashMap[String,
    mutable.HashMap[Int, Double]]): mutable.HashMap[Int, Double] = {
    val maxValues: mutable.HashMap[Int, Double] = mutable.HashMap()
    for ((_, v) <- table) { // for every word in the hashmap
      for ((key, _) <- v) { // for every doc associated with a word
        // if our hashmap does not have max val for current doc
        if (!maxValues.contains(key)) {
          maxValues.put(key, maxFinder(table, key))
        }
      }
    }
    maxValues
  }

  /**
   * calculates the relevance scores for each word-doc pair and stores them
   * in the allWords hashmap
   */
  def relevanceCalc(): Unit = {
    val maxMap: mutable.HashMap[Int, Double] = maxStore(allWords)
    val n = titles.size

    for ((_, v) <- allWords) {
      val ni = v.size // size of current v we are looping on
      val IDF = Math.log(n / ni) // IDF of current v

      for ((innerKey, innerVal) <- v) {
        val cij = innerVal // how many times current work appears in current doc
        val TF = cij / maxMap(innerKey)
        val relevance = TF * IDF
        v.put(innerKey, relevance) // put the relevance score in the hashmap
      }
    }
  }


  /**
   * calculates the weight values of every link towards the doc with our input
   * id and stores them in a hashmap
   *
   * @param currId the id of the page we are calculating weights for
   * @return a hashmap of doc id to the weight of the link from that doc to the
   *         input doc
   */
  def weightCalculator(currId: Int): mutable.HashMap[Int, Double] = {
    val weightMap:
      mutable.HashMap[Int, Double] = mutable.HashMap[Int, Double]()
    val numOfPages = this.titles.size
    val currTitle = this.titles(currId)
    val sameVal: Double =.15 / numOfPages

    for ((k, _) <- this.titles) {
      if (allLinks.contains(k)) { // if the current document links to something
        if (this.allLinks(k).contains(currTitle)) { // if the doc k links to the currDoc
          val ourNum = sameVal +.85 * (1.0 / allLinks(k).size.toDouble)
          weightMap.put(k, ourNum)
        } else { // if it doesnt link or is the same doc
          weightMap.put(k, sameVal)
        }
      } else { // if k has no links at all
        if (k == currId) { // if k is the currdoc in question
          weightMap.put(k, sameVal)
        } else {
          val ourNum = sameVal +.85 * (1.0 / (titles.size - 1.0))
          weightMap.put(k, ourNum)
        }
      }
    }
    weightMap
  }


  /**
   * returns a bool indicating whether the input string is a page in our corpus
   *
   * @param st a string of the title of a document
   * @return a boolean, true if is a page and false if it is not
   */
  def doesContain(st: String): Boolean = {
    var ourBool = false
    var k: Int = 0
    while (!ourBool & k <= titles.size) {
      if (titles.contains(k)) {
        ourBool = titles(k).equals(st)
      }
      k = k + 1
    }
    ourBool
  }

  /**
   * method to remove all links that refer to documents outside our corpus
   * from allLinks
   */
  def removeLinks(): Unit = {
    // method to remove the title if it is not in our corpus
    def listRemove(lst: List[String]): List[String] = lst match {
      case Nil => Nil
      case head :: tail =>
        if (doesContain(head)) {
          head :: listRemove(tail)
        } else {
          listRemove(tail)
        }
    }

    for ((k, _) <- allLinks) {
      val newLst = listRemove(allLinks(k))
      if (newLst.isEmpty) { // don't keep a list in allLinks if it is empty for weight calculations
        allLinks.remove(k)
      } else {
        allLinks.put(k, newLst) // add the newLst of links with only words contained in our corpus
      }
    }
  }

  /**
   * calculates the euclidean distance between the doubles that these ids map
   * to in the hashmap for pageRank
   *
   * @param firstMap  the first hashmap
   * @param secondMap the second hashmap
   * @return the euclidean distance between the doubles
   */
  def pageDistance(firstMap: mutable.HashMap[Int, Double],
                   secondMap: mutable.HashMap[Int, Double]): Double = {
    val arr: ArrayBuffer[Double] = ArrayBuffer[Double]()

    for ((k, _) <- firstMap) {
      arr += Math.pow(firstMap(k) - secondMap(k), 2.0)
    }
    Math.sqrt(arr.sum)
  }

  /**
   * makes a map with Int keys that all map to the same input number, used as
   * helper for pageRank
   *
   * @param inputMap the map with the int IDs we want to use
   * @param fillNum  the number we are mapping all the Ints to
   * @return a hashmap with Int keys all mapping to the same Double
   */
  def mapFill(inputMap: mutable.HashMap[Int, String], fillNum: Double):
  mutable.HashMap[Int, Double] = {
    val outMap: mutable.HashMap[Int, Double] = mutable.HashMap[Int, Double]()
    for ((k, _) <- inputMap) {
      outMap.put(k, fillNum)
    }
    outMap
  }

  /**
   * calculates the pageRanks of all the documents in the corpus using the
   * pageRank algorithm
   */
  def pageRank(): mutable.HashMap[Int, Double] = {
    val mapSize: Int = titles.size
    var r: mutable.HashMap[Int, Double] = mapFill(titles, 0.0)
    val rPrime:
      mutable.HashMap[Int, Double] = mapFill(titles, 1.0 / mapSize)
    removeLinks()
    while (pageDistance(rPrime, r) > .001) {
      val rPrimeCopy: mutable.HashMap[Int, Double] = rPrime.clone()
      r = rPrimeCopy // copy rPrime values into r for us to use
      for ((j, _) <- titles) {
        rPrime.put(j, 0.0) // initialize current j as 0
        val weightsMap: mutable.HashMap[Int, Double] = weightCalculator(j) // calculate weights for links to this page
        for ((k, _) <- titles) {
          rPrime.put(j, rPrime(j) + weightsMap(k) * r(k))
        }
      }
    }
    rPrime
  }

  /**
   * returns the pageRankings hashmap; this is here since pageRankings is a
   * protected var
   */
  def getRankings: mutable.HashMap[Int, Double] = {
    pageRankings
  }

  /**
   * this method sets up the whole Indexer object, a user just has to run this
   * to make all required structures
   */
  def setupAll(): Unit = {
    finalProcessor()
    relevanceCalc()
    this.pageRankings = pageRank()
  }

}

object Indexer {
  /**
   * Processes a corpus and writes to index files.
   *
   * @param args args of the form [WikiFile, titlesIndex, docsIndex,
   *             wordsIndex]
   */
  def main(args: Array[String]): Unit = {
    try {
      val ourIndexer = new Indexer(args(0))
      ourIndexer.setupAll()
      FileIO.writeTitlesFile(args(1), ourIndexer.titles) // writes hashmap of id to titles
      FileIO.writeDocsFile(args(2), ourIndexer.pageRankings) // writes hashmap of id -> rankings
      FileIO.writeWordsFile(args(3), ourIndexer.allWords) // writes hashmap of id -> (Doc -> relevance)
    } catch {
      case _: IOException => print("Invalid Input: needs four files")
    }
  }
}
