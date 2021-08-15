package sol

import tester.Tester

class SearchTester {

  def testBasicSystem(t: Tester): Unit = {
    val indexer1 = new Indexer("wikis\\PageRankExample1.xml")
    val indexer2 = new Indexer("wikis\\PageRankExample2.xml")
    val indexerOurs = new Indexer("wikis\\PageRankExampleOurs.xml")
    indexer1.setupAll()
    indexer2.setupAll()
    indexerOurs.setupAll()

    /**
     * title parsing tests
     */
    t.checkExpect(indexer1.titles(1).equals("A"), true)
    t.checkExpect(indexer1.titles(2).equals("B"), true)
    t.checkExpect(indexer1.titles(3).equals("C"), true)
    t.checkExpect(indexer2.titles(1).equals("A"), true)
    t.checkExpect(indexer2.titles(2).equals("B"), true)
    t.checkExpect(indexer2.titles(3).equals("C"), true)
    t.checkExpect(indexer2.titles(4).equals("D"), true)

    /**
     * tokenizer
     */
    t.checkExpect(
      indexer1.tokenizer("Hi, how are you",
        """\[\[[^\[]+?\]\]|[^\W_]+'[^\W_]+|[^\W_]+"""),
      List("Hi", "how", "are", "you"))

    /**
     * link splitting
     */
    val str1 = "[[Hammer:Time]]"
    val str2 = "[[Hammer|Time]]"
    t.checkExpect(indexer1.linkSplitter(":", "]",
      str1), ("Hammer", "Time"))
    t.checkExpect(indexer1.linkSplitter("|", "]",
      str2), ("Hammer", "Time"))

    /**
     * link storing
     */
    t.checkExpect(indexer1.allLinks(1), List("C", "B"))
    t.checkExpect(indexer1.allLinks.contains(2), false)
    t.checkExpect(indexer1.allLinks(3), List("A"))
    t.checkExpect(indexerOurs.allLinks.contains(1), false)
    t.checkExpect(indexerOurs.allLinks(2), List("D", "C", "A"))
    t.checkExpect(indexerOurs.allLinks(3), List("D"))
    t.checkExpect(indexerOurs.allLinks(4), List("C"))

    /**
     * system tests for pageRanking, below each is printed output since unit
     * testing isn't successful because of the
     * equals method for doubles counts past the 4 digit proficieny we are
     * wanting
     */
    val in1Rankings = indexer1.getRankings
    System.out.println(indexer1.getRankings)
    // should output: 1 -> .4326, 2 -> .2340, 3 -> .3333

    val in2Rankings = indexer2.getRankings
    System.out.println(indexer2.getRankings)
    // should output: 1 -> .2018, 2 -> .0375, 3 -> .3740, 4 -> .3867

    /**
     * this hashmap below is one personally made with edge cases in mind,
     * particularly a page that only links to
     * itself and a page that links to the same one multiple different times.
     */
    val inOursRankings = indexerOurs.getRankings
    System.out.println(indexerOurs.getRankings)
    // should output: 1 -> .0523, 2 -> .0523, 3 -> .4476, 4 -> .4476

  }
}

object SearchTester extends App {
  Tester.run(new SearchTester())
}
