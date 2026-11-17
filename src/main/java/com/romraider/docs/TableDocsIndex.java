/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2026 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.romraider.docs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The table documentation index: which Markdown page (and heading) explains a
 * table or a category, and which names in the pages' text stand for a table.
 * The format is described in docs/table_docs.md.
 *
 * <pre>
 * &lt;tabledocs base="pages" home="README.md"&gt;
 *   &lt;page file="fuel.md" title="Fuel"/&gt;
 *   &lt;table name="Main Fuel Map"&gt;
 *     &lt;doc page="fuel.md" anchor="the-main-map"/&gt;
 *   &lt;/table&gt;
 *   &lt;category name="Fuel" page="fuel.md"/&gt;
 *   &lt;alias source="tbl_Main_Map" table="Main Fuel Map"/&gt;
 * &lt;/tabledocs&gt;
 * </pre>
 *
 * Page paths are relative to the folder named by {@code base}, which is
 * relative to the index file (default: the index file's own folder).
 */
public final class TableDocsIndex {
    public static final String ROOT = "tabledocs";

    private final File indexFile;
    private final File baseDir;
    private final DocTarget home;
    private final Map<String, String> pageTitles = new LinkedHashMap<String, String>();
    private final Map<String, List<DocTarget>> tables = new LinkedHashMap<String, List<DocTarget>>();
    private final Map<String, String> tablesLower = new LinkedHashMap<String, String>();
    private final Map<String, DocTarget> categories = new LinkedHashMap<String, DocTarget>();
    private final Map<String, String> aliases = new LinkedHashMap<String, String>();

    private TableDocsIndex(File indexFile, Element root) {
        this.indexFile = indexFile;
        File dir = indexFile.getAbsoluteFile().getParentFile();
        String base = root.getAttribute("base");
        this.baseDir = base.length() == 0 ? dir : resolve(dir, base);
        String homeAttr = root.getAttribute("home");
        this.home = homeAttr.length() == 0 ? null : target(homeAttr, "");

        for (Element e : children(root, "page")) {
            String file = e.getAttribute("file");
            if (file.length() > 0) {
                pageTitles.put(resolve(baseDir, file).getPath(), e.getAttribute("title"));
            }
        }
        for (Element e : children(root, "table")) {
            String name = e.getAttribute("name");
            if (name.length() == 0) continue;
            List<DocTarget> list = tables.get(name);
            if (list == null) {
                list = new ArrayList<DocTarget>();
                tables.put(name, list);
                tablesLower.put(name.toLowerCase(Locale.ROOT), name);
            }
            // short form: <table name=".." page=".." anchor=".."/>
            if (e.getAttribute("page").length() > 0) {
                addOnce(list, target(e.getAttribute("page"), e.getAttribute("anchor")));
            }
            for (Element d : children(e, "doc")) {
                if (d.getAttribute("page").length() > 0) {
                    addOnce(list, target(d.getAttribute("page"), d.getAttribute("anchor")));
                }
            }
        }
        for (Element e : children(root, "category")) {
            String name = e.getAttribute("name");
            if (name.length() > 0 && e.getAttribute("page").length() > 0) {
                categories.put(name, target(e.getAttribute("page"), e.getAttribute("anchor")));
            }
        }
        for (Element e : children(root, "alias")) {
            String source = e.getAttribute("source");
            String table = e.getAttribute("table");
            if (source.length() > 0 && table.length() > 0) {
                aliases.put(source, table);
            }
        }
    }

    /** Reads an index file. */
    public static TableDocsIndex load(File indexFile) throws IOException {
        InputStream in = new FileInputStream(indexFile);
        try {
            return parse(in, indexFile);
        } finally {
            in.close();
        }
    }

    /**
     * Reads an index from a stream; {@code indexFile} is where it is taken to
     * be, for the relative paths.
     */
    public static TableDocsIndex parse(InputStream in, File indexFile) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            f.setExpandEntityReferences(false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.parse(in);
            Element root = doc.getDocumentElement();
            if (!ROOT.equals(root.getNodeName())) {
                throw new IOException("Not a table documentation index (root element is <"
                        + root.getNodeName() + ">, expected <" + ROOT + ">): " + indexFile);
            }
            return new TableDocsIndex(indexFile, root);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Cannot read the table documentation index " + indexFile + ": "
                    + e.getMessage(), e);
        }
    }

    public File getIndexFile() {
        return indexFile;
    }

    /** The folder the page paths are relative to. */
    public File getBaseDir() {
        return baseDir;
    }

    /** The page to show when nothing is selected, or null. */
    public DocTarget getHome() {
        return home;
    }

    /**
     * The places that explain a table, best first; an empty list when the
     * index does not name the table. The name is matched exactly first, then
     * without regard to case.
     */
    public List<DocTarget> forTable(String tableName) {
        if (tableName == null) return Collections.emptyList();
        List<DocTarget> list = tables.get(tableName);
        if (list == null) {
            String exact = tablesLower.get(tableName.toLowerCase(Locale.ROOT));
            list = exact == null ? null : tables.get(exact);
        }
        return list == null ? Collections.<DocTarget>emptyList() : Collections.unmodifiableList(list);
    }

    /** The page for a category, or null. */
    public DocTarget forCategory(String category) {
        if (category == null) return null;
        DocTarget t = categories.get(category);
        if (t == null) {
            for (Map.Entry<String, DocTarget> e : categories.entrySet()) {
                if (e.getKey().equalsIgnoreCase(category)) return e.getValue();
            }
        }
        return t;
    }

    /**
     * The places for a table, or else its category's page; empty when the
     * index has neither.
     */
    public List<DocTarget> lookup(String tableName, String category) {
        List<DocTarget> list = forTable(tableName);
        if (!list.isEmpty()) return list;
        DocTarget c = forCategory(category);
        return c == null ? Collections.<DocTarget>emptyList() : Collections.singletonList(c);
    }

    /** The table display name a name in the page text stands for, or null. */
    public String tableForName(String name) {
        if (name == null) return null;
        String t = aliases.get(name);
        if (t != null) return t;
        return tables.containsKey(name) ? name : null;
    }

    /**
     * Every name in the page text that stands for a table: the aliases and the
     * table display names themselves.
     */
    public Map<String, String> getLinkNames() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (String t : tables.keySet()) m.put(t, t);
        m.putAll(aliases);
        return Collections.unmodifiableMap(m);
    }

    /** The names in the page text that stand for the given table. */
    public Set<String> namesForTable(String tableName) {
        Set<String> s = new LinkedHashSet<String>();
        if (tableName == null) return s;
        s.add(tableName);
        for (Map.Entry<String, String> e : aliases.entrySet()) {
            if (e.getValue().equals(tableName)) s.add(e.getKey());
        }
        return s;
    }

    public Set<String> getTableNames() {
        return Collections.unmodifiableSet(tables.keySet());
    }

    public Set<String> getCategoryNames() {
        return Collections.unmodifiableSet(categories.keySet());
    }

    /** Every page the index names, in the order first named. */
    public List<File> getPages() {
        Set<File> s = new LinkedHashSet<File>();
        for (String p : pageTitles.keySet()) s.add(new File(p));
        if (home != null) s.add(home.getPage());
        for (List<DocTarget> l : tables.values()) for (DocTarget t : l) s.add(t.getPage());
        for (DocTarget t : categories.values()) s.add(t.getPage());
        return new ArrayList<File>(s);
    }

    /** The title the index gives a page, or "". */
    public String getPageTitle(File page) {
        String t = pageTitles.get(page.getPath());
        return t == null ? "" : t;
    }

    private DocTarget target(String page, String anchor) {
        String a = anchor == null ? "" : anchor.trim();
        if (a.startsWith("#")) a = a.substring(1);
        return new DocTarget(resolve(baseDir, page), a);
    }

    private static File resolve(File dir, String path) {
        File f = new File(path.replace('/', File.separatorChar));
        if (!f.isAbsolute()) f = new File(dir, path.replace('/', File.separatorChar));
        try {
            return f.getCanonicalFile();
        } catch (IOException e) {
            return f.getAbsoluteFile();
        }
    }

    private static void addOnce(List<DocTarget> list, DocTarget t) {
        if (!list.contains(t)) list.add(t);
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> l = new ArrayList<Element>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && name.equals(n.getNodeName())) {
                l.add((Element) n);
            }
        }
        return l;
    }
}
