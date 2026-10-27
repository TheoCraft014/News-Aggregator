import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;


public class MyThread extends Thread {
    private final int id;
    private BlockingQueue<String> queue;
    private List<Article> localArticles;

    public MyThread(int id, BlockingQueue<String> q){
        this.id = id;
        queue = q;
        localArticles = new ArrayList<>();
    }

    @Override
    public void run() {
        // 1 parsare JSON
        while(true) {
            String path = queue.poll();
            if (path == null)
                break;
            // https://stackoverflow.com/questions/9917787/merging-two-arraylists-into-a-new-arraylist-with-no-duplicates-and-in-order-in
            List<Article> parsedArt = JsonInput.deserializeAccounts(path);

            if (parsedArt != null) {
                for (Article article : parsedArt) {

                    NewsAggregator.totalParsed.incrementAndGet();

                    addCount(NewsAggregator.uidds, article.getUuid());
                    addCount(NewsAggregator.titles, article.getTitle());

                    localArticles.add(article);
                }
            }
        }

        //am terminat parsarea si eliminarea duplicatelor - astept sa termine toate thread-urile
        barrierAwait();

        // creez o lista locala ce retine doar articole
        // in engleza
        // pentru a face cuvintele de interes
        List<Article> localEnglishArt = new ArrayList<>();

        // 2 eliminare duplicate si asocieri limba/categorie cu uuid-ul articolului
        for (Article art : localArticles) {
                if (NewsAggregator.uidds.get(art.getUuid()) > 1 ||
                    NewsAggregator.titles.get(art.getTitle()) > 1) {
                    continue; // e duplicat deci merg mai departe
                }

                NewsAggregator.articles.add(art);

                String language = art.getLanguage();
                if (NewsAggregator.langFiles.contains(language)) {

                    NewsAggregator.langMap.computeIfAbsent(language,
                            k -> Collections.synchronizedSet(new TreeSet<>())).add(art.getUuid());
                }

                for (String cat : art.getCategories()) {
                    if (NewsAggregator.catFiles.contains(cat)) {
                        // elimina virgula, whitespace si inlocuieste spatiile cu underline
                        String editCat = cat.replace(",", "").trim()
                                .replaceAll("\\s+", "_");
                        NewsAggregator.catMap.computeIfAbsent(editCat,
                                k -> Collections.synchronizedSet(new TreeSet<>())).add(art.getUuid());
                    }
                }

                if (language.equals("english")) {
                    localEnglishArt.add(art);
                }
        }

        barrierAwait();

        // 3. gasirea cuvintelor de interes

        // intai parsez toate articolele scrise in engleza
        for (Article art : localEnglishArt) {
            // extrag cuvintele
            String[] cuv = art.getText().toLowerCase().split("\\s+");
            Set<String> uniqueCuv = new HashSet<>();

            for (String c : cuv) {
                // inlocuiesc toti non-literalii
                c = c.replaceAll("[^a-z]", "");

                // daca cuvantul dat nu se afla la linking-files atunci
                // il adaug in Set
                if (!NewsAggregator.linkingFiles.contains(c) && !c.isEmpty()) {
                    uniqueCuv.add(c);
                }
            }

            for (String c : uniqueCuv) {
                // adaug lobal cuvintele de interes din engleza
                addCount(NewsAggregator.linkingMap, c);
            }
        }

        barrierAwait();

        // generez keywords_count.txt doar in thread-ul cu id = 0
        if (id == 0) {
            generateKeyWordsFile();
        }

        // 4. generare fisiere pentru limbi si categorii folosind coada
        // asigur ca fiecare thread lucreaza in generarea unui fisier in paralel
        while(true) {
            String lang = NewsAggregator.langQueue.poll();
            if (lang == null)
                break;

            if (NewsAggregator.langMap.get(lang) != null) {
                createFile(lang + ".txt", NewsAggregator.langMap.get(lang));
            }
        }

        while(true) {
            String cat = NewsAggregator.catQueue.poll();
            if (cat == null)
                break;

            if (NewsAggregator.catMap.get(cat) != null) {
                createFile(cat + ".txt", NewsAggregator.catMap.get(cat));
            }
        }

        if (id == 0) {
            // scrierea intr-un fisier se face in paralel
            generateArticles();
        }

        // 5 generam raportul general - e ok secventia
        if (NewsAggregator.NUM_THREADS == 1 && id == 0 || NewsAggregator.NUM_THREADS > 1 && id == 1) {
            generateReports();
        }
    }

    private void generateReports() {

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter("reports.txt"));

            // duplicates found
            int totalArt = NewsAggregator.totalParsed.get();
            int uniqueArt = NewsAggregator.articles.size();
            int duplicates = totalArt - uniqueArt;

            bw.write("duplicates_found - " + duplicates);
            bw.newLine();

            // unique articles
            bw.write("unique_articles - " + uniqueArt);
            bw.newLine();

            // toate celelalte au aceeasi implementare
            // creez map si le parsez pentru a gasi
            // elementul dorit prin comparatii
            // lexicografice sau numerice cu celelalte elemente

            // best author
            Map<String, Integer> authorCount = new HashMap<>();

            // retin nr de aparitii ale fiecarui autor
            for (Article a : NewsAggregator.articles) {
                authorCount.merge(a.getAuthor(), 1, Integer::sum);
            }

            String bestAuthor = null;
            int bestAuthorCount = 0;

            // parsez in map si compar
            for (Map.Entry<String, Integer> e : authorCount.entrySet()) {
                if (bestAuthor == null
                        || e.getValue() > bestAuthorCount
                        || (e.getValue() == bestAuthorCount
                        && e.getKey().compareTo(bestAuthor) < 0)) {

                    bestAuthor = e.getKey();
                    bestAuthorCount = e.getValue();
                }
            }

            bw.write("best_author - " + bestAuthor + " " + bestAuthorCount);
            bw.newLine();

            // top language
            String topLang = null;
            int topLangCount = 0;

            for (Map.Entry<String, Set<String>> e : NewsAggregator.langMap.entrySet()) {
                int size = e.getValue().size();
                if (topLang == null
                        || size > topLangCount
                        || (size == topLangCount
                        && e.getKey().compareTo(topLang) < 0)) {

                    topLang = e.getKey();
                    topLangCount = size;
                }
            }

            bw.write("top_language - " + topLang + " " + topLangCount);
            bw.newLine();

            // top category
            String topCat = null;
            int topCatCount = 0;

            for (Map.Entry<String, Set<String>> e : NewsAggregator.catMap.entrySet()) {
                int size = e.getValue().size();
                if (topCat == null
                        || size > topCatCount
                        || (size == topCatCount
                        && e.getKey().compareTo(topCat) < 0)) {

                    topCat = e.getKey();
                    topCatCount = size;
                }
            }

            bw.write("top_category - " + topCat + " " + topCatCount);
            bw.newLine();

            // most recent article
            Article newest = null;

            for (Article a : NewsAggregator.articles) {
                if (newest == null
                        || a.getPublished().compareTo(newest.getPublished()) > 0
                        || (a.getPublished().equals(newest.getPublished())
                        && a.getUuid().compareTo(newest.getUuid()) < 0)) {

                    newest = a;
                }
            }

            bw.write("most_recent_article - "
                    + newest.getPublished() + " "
                    + newest.getUrl());
            bw.newLine();


            // top keyword en
            String bestKeyword = null;
            int bestKeywordCount = 0;

            for (Map.Entry<String, Integer> e : NewsAggregator.linkingMap.entrySet()) {
                if (bestKeyword == null
                        || e.getValue() > bestKeywordCount
                        || (e.getValue() == bestKeywordCount
                        && e.getKey().compareTo(bestKeyword) < 0)) {

                    bestKeyword = e.getKey();
                    bestKeywordCount = e.getValue();
                }
            }

            bw.write("top_keyword_en - " + bestKeyword + " " + bestKeywordCount);
            bw.newLine();

            bw.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void generateKeyWordsFile() {
        try {
            // creez lista ca sa o pot sorta
            List<Map.Entry<String, Integer>> list = new ArrayList<>(NewsAggregator.linkingMap.entrySet());

            // sortez descrescator dupa nr de articole
            // lexicografic la nr egal
            list.sort((a, b) -> {
                int cmp = Integer.compare(b.getValue(), a.getValue());
                if (cmp == 0) {
                    // sortare lexicografica
                    return a.getKey().compareTo(b.getKey());
                }
                return cmp;

            });

            BufferedWriter bw = new BufferedWriter(new FileWriter("keywords_count.txt"));

            for (Map.Entry<String, Integer> pair : list) {
                bw.write(pair.getKey() + " " + pair.getValue());
                bw.newLine();
            }

            bw.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void generateArticles() {

        // copiez intr-o lista locala
        List<Article> copyList;
        synchronized (NewsAggregator.articles) {
            copyList = new ArrayList<>(NewsAggregator.articles);
        }

        // sortare descrescator dupa published
        // daca e egal sortez lexicografic cu uidd
        // https://stackoverflow.com/questions/12542185/sort-a-java-collection-object-based-on-one-field-in-it
        copyList.sort((a, b) -> {
            // compar descrescator published
            // timpul coincide cu ordinea lexicografica
            int cmp = b.getPublished().compareTo(a.getPublished());

            if (cmp != 0) {
                return cmp;
            }

            // daca e aceeasi ordine, compar dupa uuid
            return a.getUuid().compareTo(b.getUuid());
        });

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter("all_articles.txt"));

            for (Article art : copyList) {
                bw.write(art.getUuid() + " " + art.getPublished());
                bw.newLine();
            }

            bw.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // modularizare pentru asteptarea barierei
    // folosita cand se asteapta ca toate thread-urile sa termine sarcina
    private void barrierAwait() {
        try {
            //Resincronizarea thread-urilor pentru urmatorul pas al algoritmului.
            NewsAggregator.barrier.await();
        } catch (BrokenBarrierException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    // schimb cheia hashmap-ului thread safe
    private void addCount(ConcurrentHashMap<String, Integer> m, String key) {
        while(true) {
            Integer val = m.putIfAbsent(key, 1);
            if (val == null) {
                // s-a inserat 1
                break;
            } else {
                // cheia exista, o incrementez
                if (m.replace(key, val, val + 1)) {
                    break;
                }
            }
        }
    }

    // creez fisierul
    private void createFile(String filename, Set<String> uuids) {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
            for (String uuid : uuids) {
                bw.write(uuid);
                bw.newLine();
            }

            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
