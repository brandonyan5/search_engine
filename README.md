# search_engine
Created a search engine that processes XML files and outputs top results based on a user inputted query


Interaction
- To interact with the indexer, there's a setupAll method which does the work for the user. If someone wants to just
    try indexing an xml file, they can input that file in a new Indexer object and run the setupAll method in a tester
    file. This will process all words and doc-word relevances, run and store the results of pageRank, and give a list
    of titles inside the indexer object.
- For indexer, one can also pass in an xml and three .txt files into the terminal and running it with the scala command.
    Here the program will write the titles, doc-word relevances, and pageRank results to the text files which will be
    ready for use by the querier.
- For Querier, one can either run it in the terminal in the order of "titles.txt docs.txt words.txt" to query the files
    and initiate a search query inside the terminal. Or one can create a querier object with these files in the
    constructor and run the getResults method to see which documents are associated with the input string.

Brief Overview
- Indexer
    - pageProcessor
        - The largest and arguably most important method in the code. This method is for processing a single page of
            data that gets inputted into the program. It uses list recursion to process every word individually. It
            checks for stopWords, stems words, and deals with links. It does most of the processing work
    - finalProcessor
        - This method runs pageProcessor over the whole xml file of data. It also parses and makes the list
            of titles as the program is processing a page. All the information needed from a document is taken one page at a
            time. After this has run, there is a unique words hashmap, a titles hashmap, and a list of all links.
    - relevanceCalc
        - This calculates the doc-word relevance for every word and document pair. The corresponding TF and IDF scores
            are calculated inside the method to save space. One thing notable is that this overrides the current words
            hashmap so now allWords links from word => (docid => word relevance)
    - pageRank
        - This method returns a hashmap of doc ids to a double representing their pageRank values. The large helper for
            this is called weightsCalculator which calculates all the weights from every page to a document j.
    - setupAll
        - This method is used for convenient setup of the an Indexer. All one has to do is run this method and all the
            appropriate data has been processed and converted into their data structures.
- Querier
    - getResults
    - runRepl

Testing
- SearchTester
    - This is the file that contains the testing outside of most system testing. It includes various unit tests for
        methods and helper methods inside the Indexer.
- PageRank
    - The pageRank tests are included inside the SearchTester. For pageRank, I tested it on the two provided documents
        with example ranks and my own document. This document: "PageRankExampleOurs.xml" contains all the edge cases
        I need to deal with when considering pageRank. It is worth noting that the pageRank outputs the expected
        pageRankings for all 3 of these tests. These system tests and their outputs are in SearchTester for reference



