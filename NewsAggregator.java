import java.io.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class NewsAggregator {
    // --- ARGUMENTE ---
    // nr thread-uri
    public static int NUM_THREADS;

    // reprezinta fisierele articles.txt si inputs.txt
    public static String articlesI, inputsI;

    // --- FISIERE AUXILIARE --- //
    // languages.txt
    public static List<String> langFiles;

    // categories.txt
    public static List<String> catFiles;

    // english_linking_words.txt
    public static List<String> linkingFiles;

    // -- MAPS --
    // retine ce limba are un uuid
    public static ConcurrentHashMap<String, Set<String>> langMap = new ConcurrentHashMap<>();

    // retine ce categorie are un uidd
    public static ConcurrentHashMap<String, Set<String>> catMap = new ConcurrentHashMap<>();

    // retine de cate ori a aparut un cuvant cheie
    public static ConcurrentHashMap<String, Integer> linkingMap = new ConcurrentHashMap<>();

    // -- COZI --
    // populez coada cu categoriile si limbile
    public static BlockingQueue<String> langQueue;
    public static BlockingQueue<String> catQueue;

    // bariera are loc de a astepta ca toate thread-urile sa termine o instructiune
    // pentru a merge mai departe la urmatorul set de instructiuni
    public static CyclicBarrier barrier;

    // lista de articole
    public static List<Article> articles = Collections.synchronizedList(new ArrayList<>());

    public static AtomicInteger totalParsed = new AtomicInteger(0);
    // --- ELIMINAREA DUPLICATELOR ---

    // retine numarul de aparitii pentru fiecare uidd sau titlu
    public static ConcurrentHashMap<String, Integer> uidds;
    public static ConcurrentHashMap<String, Integer> titles;

    // dinamica consumator - producator thread-safe
    // tip linked list deoarece avem dimensiune variabila
    public static BlockingQueue<String> fileQ;

    // procesez fisierele de input
    // sursa: https://www.w3schools.com/java/java_files_read.asp

    // GESTIONAREA CAILOR ABSOLUTE SI RELATIVE
    public static File parentDirInputs;
    public static File parentDirArticles;

    private static void processInputsFiles() throws IOException {
        // aflu caile absolute pentru fisier
        File articlesFile = new File(articlesI);
        parentDirArticles = articlesFile.getParentFile();

        File inputFile = new File(inputsI);
        parentDirInputs = inputFile.getParentFile();

        BufferedReader brI = new BufferedReader(new FileReader(inputsI));

        // obtin caile relative pentru fisiere
        int count = Integer.parseInt(brI.readLine().trim());
        String languagesPath = brI.readLine().trim();
        String categoriesPath = brI.readLine().trim();
        String linkingPath = brI.readLine().trim();

        brI.close();

        // preiau fisierele
        File langFileF = new File(parentDirInputs, languagesPath);
        File catFileF = new File(parentDirInputs, categoriesPath);
        File linkingFileF = new File(parentDirInputs, linkingPath);

        // preiau datele din fisier si le retin in lista
        langFiles = parseInputsFilesData(langFileF.getAbsolutePath());
        catFiles = parseInputsFilesData(catFileF.getAbsolutePath());
        linkingFiles = parseInputsFilesData(linkingFileF.getAbsolutePath());
    }

    private static List<String> parseInputsFilesData(String filePath) throws IOException {
        List<String> list = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        int nr = Integer.parseInt(br.readLine().trim());

        for (int i = 0; i < nr; i++) {
            list.add(br.readLine().trim());
        }

        br.close();
        return list;
    }

    private static void populateQueues() {
        // coada pt limbi
        langQueue.addAll(langFiles);

        for (String cat : catFiles) {
            // elimin virgulele, whitespace si inlocuiesc spatiile cu underline
            String edit = cat.replace(",", "")
                    .trim()
                    .replaceAll("\\s+", "_");
            catQueue.add(edit);
        }

    }

    public static void main(String[] argc) {
        NUM_THREADS = Integer.parseInt(argc[0]);
        articlesI = argc[1];
        inputsI = argc[2];

        uidds = new ConcurrentHashMap<>();
        titles = new ConcurrentHashMap<>();
        linkingMap = new ConcurrentHashMap<>();

        barrier = new CyclicBarrier(NUM_THREADS);

        try {
            // retin fisierele de input:
            // categorii etc
            processInputsFiles();

            langQueue = new LinkedBlockingQueue<>();
            catQueue = new LinkedBlockingQueue<>();
            populateQueues();

            fileQ = new LinkedBlockingQueue<>();
            BufferedReader pathReader = new BufferedReader(new FileReader(articlesI));
            int num = Integer.parseInt(pathReader.readLine());

            // pun secvential in lista de cai
            for (int i = 0; i < num; i++) {
                // calea relativa
                String JsonPath = pathReader.readLine().trim();
                File JsonFile = new File(parentDirArticles, JsonPath);

                // calea absoluta
                fileQ.add(JsonFile.getAbsolutePath());
            }
            pathReader.close();

            // https://www.geeksforgeeks.org/java/cyclicbarrier-in-java-with-examples/
            // doresc ca fiecare thread sa
            //CyclicBarrier bariera = new CyclicBarrier(NUM_THREADS + 1);
            Thread[] threads = new Thread[NUM_THREADS];

            for (int i = 0; i < NUM_THREADS; ++i) {
                threads[i] = new MyThread(i, fileQ);
                threads[i].start();
            }

            for (int i = 0; i < NUM_THREADS; ++i) {
                try {
                    threads[i].join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
