# Table documentation panel

The editor can show a Markdown page beside the table you are looking at: the page that explains the table, scrolled
to the heading that covers it, with the table's name marked. A definition author ships the pages and an index file
that says which page explains which table; the user points RomRaider at the index once.

Without an index nothing changes: no panel, no extra work at start-up.

## Using it

* **View > Table documentation > Choose index file...** picks the index (`.xml`). The panel opens to the right of the
  table area. The choice is kept in `settings.xml` (`<tabledocs index="..." width="..." visible="..."/>` under
  `<files>`), with the panel's width and whether it is shown.
* Select a table in the tree, or bring a table window to the front: the panel shows its page at its heading and marks
  the table's name in the text. A table the index does not name shows its category's page; a table with neither
  shows the list of pages.
* When several places explain a table, a list under the title goes to the others.
* Links between pages stay in the panel; **Back** and **Forward** go through them. A link to a table opens it (or
  brings it forward) in the editor. Web links and links to other files open in the system browser.
* **Pin** keeps the page while you select other tables. **Open in browser** shows the page in the system browser,
  diagrams included. The cross hides the panel; **Show beside the tables** in the same menu brings it back.

## Index file

An XML file. Page paths are relative to the folder named by `base`, which is relative to the index file itself (no
`base`: the index file's folder).

```xml
<tabledocs base="pages" home="README.md">
  <!-- optional: titles for the list of pages (else each page's first heading) -->
  <page file="idle.md" title="Idle speed control"/>

  <!-- a table, by its name as the definition shows it: one or more places, best first -->
  <table name="Base Fuel">
    <doc page="fuel.md" anchor="base-fuel"/>
    <doc page="idle.md" anchor="control-loop"/>
  </table>
  <!-- short form for one place -->
  <table name="Fuel Pump Duty" page="fuel.md" anchor="fuel-pump"/>

  <!-- a category (as in the definition): the page for its tables that have none of their own -->
  <category name="Fuel" page="fuel.md"/>

  <!-- a name used in the pages' text for a table: a code span `tbl_Base_Fuel` becomes a link that opens it -->
  <alias source="tbl_Base_Fuel" table="Base Fuel"/>
</tabledocs>
```

| Element | Attributes | Meaning |
| --- | --- | --- |
| `tabledocs` | `base`, `home` (both optional) | The root. `home` is shown before any table is selected. |
| `page` | `file`, `title` | A page and its title for the list of pages. Optional. |
| `table` | `name`, optional `page` and `anchor` | A table. Names match exactly, else without regard to case. |
| `doc` | `page`, `anchor` | One place that explains the table (inside `table`). |
| `category` | `name`, `page`, optional `anchor` | The page for a category. |
| `alias` | `source`, `table` | A code span whose whole text is `source` links to the table. |

`anchor` is a heading id as GitHub makes it: lower case, spaces to hyphens, other punctuation dropped, `-1`, `-2` for
repeated headings ("Steps of the lookup table" is `steps-of-the-lookup-table`). A leading `#` is allowed. Without
an anchor the page opens at the top. A code span that is a table's own name also links to it.

A complete example with made-up pages is in [table_docs_example/](table_docs_example/table_docs.xml).

## Pages

Markdown as on GitHub: tables, heading ids, strikethrough, fenced code. Also:

* **Mermaid** diagrams in ` ```mermaid ` blocks (flowcharts, state diagrams and the rest), drawn offline by the
  bundled Mermaid 11 (`src/main/resources/com/romraider/docs/mermaid.min.js`).
* **Figures** as images (`![text](folder/figure.svg)`), relative to the page. SVG, PNG and JPEG.
* **Links** to other pages (`[text](other.md#heading)`), to headings on the same page (`#heading`), to the web, and
  to tables (code spans named in the index, or `romraider-table:Table%20Name` written as a link).

## JavaFX: diagrams and SVG figures in the panel

The panel draws Mermaid diagrams and SVG figures with a JavaFX WebView. JavaFX is optional, at build time and at run
time:

* **Build** with a JavaFX SDK (from [Gluon](https://gluonhq.com/products/javafx/), the version of the JDK, unpacked
  anywhere): `ant -Djavafx.sdk=C:\Tools\javafx-sdk-25.0.5 build-windows`, or set the environment variable
  `JAVAFX_SDK` to that folder. Without it the build leaves out `com.romraider.docs.fx` and works as before.
* **Run** with the SDK's modules added:

  ```
  java --module-path C:\Tools\javafx-sdk-25.0.5\lib --add-modules javafx.swing,javafx.web ^
       --enable-native-access=javafx.graphics,javafx.web,javafx.media,ALL-UNNAMED ...
  ```

Without JavaFX (not in the build, or not given at run time) the panel uses Swing's HTML view: text, tables, links and
PNG/JPEG figures; each Mermaid diagram is shown as its source and each SVG figure as a link, and a line at the top
says how to get the full view. **Open in browser** shows everything either way. `-Dromraider.docs.renderer=swing`
chooses the Swing view even when JavaFX is there.

## Code

`com.romraider.docs`: `TableDocsIndex` (the index), `MarkdownPages` (Markdown to HTML with commonmark-java),
`DocLink` (what a clicked link is), `PageStore` (converted pages, temporary HTML files), `TableDocsPanel` (the panel:
a plain `JComponent` with `setIndex`, `showTable`, `setTableOpener`), `EditorDocs` (puts the panel into the editor
window and adds the menu), `SwingDocsRenderer` and `fx.FxDocsRenderer` (the two views). Tests:
`src/test/java/com/romraider/docs/TableDocsTest.java`.

Third-party files: `lib/common/commonmark-*.jar` (commonmark-java 0.32.0, BSD 2-Clause,
`lib/common/commonmark-java-LICENSE.txt`) and `mermaid.min.js` (Mermaid 11.17.2, MIT, with its bundled libraries'
licenses listed in `src/main/resources/com/romraider/docs/mermaid-LICENSE.txt`).
