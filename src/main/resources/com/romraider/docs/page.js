// Behaviour of a documentation page in RomRaider's documentation panel.
// window.rrTarget = {anchor, table} is set before this script.
(function () {
    var userMoved = false;

    function heading(anchor) {
        if (!anchor) return null;
        var e = document.getElementById(anchor);
        if (!e) e = document.getElementById(decodeURIComponent(anchor));
        return e;
    }

    // Marks the table's links and the heading, and scrolls to them.
    window.rrShow = function (anchor, table) {
        window.rrTarget = {anchor: anchor || "", table: table || null};
        var old = document.querySelectorAll(".rr-current, .rr-target-heading");
        for (var i = 0; i < old.length; i++) {
            old[i].classList.remove("rr-current");
            old[i].classList.remove("rr-target-heading");
        }
        var first = null;
        if (table) {
            var links = document.querySelectorAll("a.rr-table");
            for (var j = 0; j < links.length; j++) {
                if (links[j].getAttribute("data-table") === table) {
                    links[j].classList.add("rr-current");
                    if (!first) first = links[j];
                }
            }
        }
        var h = heading(anchor);
        if (h && table) h.classList.add("rr-target-heading");
        var to = h || first;
        if (to) {
            to.scrollIntoView();
        } else {
            window.scrollTo(0, 0);
        }
        userMoved = false;
        return to ? "shown" : "top";
    };

    // Counts of marked links, for the host.
    window.rrMarked = function () {
        return document.querySelectorAll("a.rr-current").length;
    };

    // Links go to the host when there is one (the documentation panel, which takes alerts).
    document.addEventListener("click", function (ev) {
        var a = ev.target;
        while (a && a.nodeName !== "A") a = a.parentNode;
        if (!a || !a.getAttribute("href")) return;
        if (window.rrHost) {
            ev.preventDefault();
            alert("rr-link:" + a.getAttribute("href"));
        }
    }, true);

    ["wheel", "keydown", "mousedown"].forEach(function (t) {
        window.addEventListener(t, function () { userMoved = true; }, true);
    });

    window.rrMermaid = {ok: 0, errors: 0, done: false, messages: []};

    // Now, before the load event: Mermaid would otherwise draw the blocks itself on load.
    var blocks = document.querySelectorAll("pre.mermaid");
    var sources = [];
    for (var b = 0; b < blocks.length; b++) sources.push(blocks[b].textContent);
    if (typeof mermaid !== "undefined") {
        mermaid.initialize({startOnLoad: false, securityLevel: "strict", theme: "default"});
    }

    async function renderMermaid() {
        if (blocks.length === 0 || typeof mermaid === "undefined") {
            window.rrMermaid.done = true;
            window.rrMermaid.missing = blocks.length > 0;
            return;
        }
        for (var i = 0; i < blocks.length; i++) {
            var el = blocks[i];
            var src = sources[i];
            try {
                var res = await mermaid.render("rr-mermaid-" + i, src);
                el.innerHTML = res.svg;
                window.rrMermaid.ok++;
            } catch (e) {
                window.rrMermaid.errors++;
                var msg = String(e && e.message ? e.message : e);
                window.rrMermaid.messages.push(msg.substring(0, 300));
                el.classList.add("rr-mermaid-error");
                el.textContent = "Diagram error: " + msg + "\n\n" + src;
                var stray = document.getElementById("drr-mermaid-" + i);
                if (stray) stray.remove();
            }
        }
        window.rrMermaid.done = true;
    }

    rrShow(window.rrTarget.anchor, window.rrTarget.table);
    function afterLoad() {
        renderMermaid().then(function () {
            if (!userMoved) rrShow(window.rrTarget.anchor, window.rrTarget.table);
        });
    }
    if (document.readyState === "complete") {
        afterLoad();
    } else {
        window.addEventListener("load", afterLoad);
    }
})();
