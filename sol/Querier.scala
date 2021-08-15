package sol

import src.{FileIO, IQuerier, PorterStemmer, StopWords}

import java.io.{BufferedReader, IOException, InputStreamReader}
import scala.collection.mutable

/**
 * Class for a querier REPL that uses index files built from a corpus.
 *
 * @param titleIndex    - the filename of the title index
 * @param documentIndex - the filename of the document index
 * @param wordIndex     - the filename of the word index
 * @param usePageRank   - true if PageRank is to be used to rank results
 */
class Querier(titleIndex: String, documentIndex: String, wordIndex: String,
              usePageRank: Boolean) extends IQuerier {

  /**
   * hashmap that stores the doc ids as the keys, and each value is the title
   * of the document associated with that id
   */
  val titlesMap: mutable.HashMap[Int, String] = mutable.HashMap()

  /**
   * hashmap that stores each term in the corpus as a key. Each key maps to
   * another hashmap, where the keys are the doc ids in which that term
   * appears in, and the values are the relevance scores
   */
  val wordsMap:
    mutable.HashMap[String, mutable.HashMap[Int, Double]] = mutable.HashMap()

  /**
   * hashmap that stores the doc ids as the keys, and each key is mapped to
   * the pagerank score for that document
   */
  val docsMap: mutable.HashMap[Int, Double] = mutable.HashMap()

  FileIO.readTitles(titleIndex, titlesMap)
  FileIO.readWordsFile(wordIndex, wordsMap)
  FileIO.readDocsFile(documentIndex, docsMap)

  override def getResults(query: String): List[Int] = {
    //parsing the input query into an array of strings
    //with only the words we need
    val listOfInputWords: Array[String] = query.split(" ")
      .filter(x => !StopWords.isStopWord(x))
      .map(y => PorterStemmer.stem(y)).map(z => z.toLowerCase())

    //this hashmap stores the final scores for each relevant doc id
    val docScores: mutable.HashMap[Int, Double] = mutable.HashMap()

    //populates docScores by accumulating scores
    for (inputWord <- listOfInputWords) {
      if (wordsMap.contains(inputWord)) {
        for ((docID, score) <- wordsMap(inputWord)) {
          if (!docScores.contains(docID)) {
            docScores.put(docID, score)
          } else {
            docScores.update(docID, docScores(docID) + score)
          }
        }
      }
    }

    //multiplies by pagerank score if user wants to use pagerank
    if (usePageRank) {
      for ((k, v) <- docScores) {
        docScores.update(k, v * docsMap(k))
      }
    }

    //sorts the ids by the associated scores
    val finalListSortedDocs = docScores.keys.toList
      .sortBy(x => -1 * docScores(x))
    var topTenDocs: List[Int] = List()

    //populates an empty list with the final list of ids
    for (i <- 0 until 10) {
      if (i > finalListSortedDocs.length - 1) {
        return topTenDocs
      } else {
        topTenDocs = topTenDocs.appended(finalListSortedDocs(i))
      }
    }
    topTenDocs
  }

  override def runRepl(): Unit = {
    val reader = new BufferedReader(new InputStreamReader(System.in));

    print("Enter a query:")

    var input = reader.readLine()

    //loop that prompts for query and processes it
    while (input != ":quit" && input != null) {
      if (getResults(input) == Nil) {
        print("Sorry, no results were found")
      } else {
        var i = 1
        for (printWord <- getResults(input).map(w => titlesMap(w))) {
          print("\n" + i.toString + ": " + printWord)
          i = i + 1
        }
      }
      System.out.println("\n\nPlease enter a query:")
      input = reader.readLine()
    }
    reader.close()
  }
}

object Querier {
  /**
   * Runs the querier REPL.
   *
   * @param args args of the form [--pageRank (optional), titlesIndex,
   *             docsIndex, wordsIndex]
   */
  def main(args: Array[String]): Unit = {
    try {
      if (args(0).equals("--pagerank")) {
        val query = new Querier(args(1), args(2), args(3), true)
        query.runRepl()
      } else {
        val query = new Querier(args(0), args(1), args(2), false)
        query.runRepl()
      }
    } catch {
      case e: IOException => print("Invalid Input: please input valid files")
      case e2: ArrayIndexOutOfBoundsException =>
        print("Please enter at least" +
          " 3 arguments")
    }
  }
}
