"use strict";
(() => {
  const DB_NAME = "rovex-companion-poc";
  const DB_VERSION = 1;
  const MAX_IMPORT_BYTES = 25 * 1024 * 1024;
  const stores = ["questions", "progress", "notes"];
  const $ = id => document.getElementById(id);
  const state = { questions: [], progress: new Map(), subject: "", search: "" };
  const demo = [
    { id: "demo-anat-001", subject: "Anatomy", category: "Sample", stem: "Which structure passes through the foramen ovale?", options: ["Maxillary nerve (V2)", "Mandibular nerve (V3)", "Middle meningeal artery", "Ophthalmic nerve (V1)"], answerIndex: 1, explanation: "The mandibular division of the trigeminal nerve (V3) passes through the foramen ovale.", tags: ["cranial nerves"] },
    { id: "demo-phys-001", subject: "Physiology", category: "Sample", stem: "Which change shifts the oxyhaemoglobin dissociation curve to the right?", options: ["Reduced temperature", "Reduced 2,3-BPG", "Increased hydrogen ion concentration", "Reduced PCO₂"], answerIndex: 2, explanation: "Increased H⁺ (lower pH) decreases haemoglobin affinity for oxygen and shifts the curve to the right.", tags: ["respiratory physiology"] },
    { id: "demo-pharm-001", subject: "Pharmacology", category: "Sample", stem: "Which drug is a non-selective beta-adrenergic antagonist?", options: ["Metoprolol", "Atenolol", "Propranolol", "Nebivolol"], answerIndex: 2, explanation: "Propranolol blocks both β₁ and β₂ receptors.", tags: ["autonomics"] }
  ];
  function openDb() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains("questions")) db.createObjectStore("questions", { keyPath: "id" });
        if (!db.objectStoreNames.contains("progress")) db.createObjectStore("progress", { keyPath: "questionId" });
        if (!db.objectStoreNames.contains("notes")) db.createObjectStore("notes", { keyPath: "id" });
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error("Unable to open local database"));
    });
  }
  async function readAll(storeName) {
    const db = await openDb();
    try {
      return await new Promise((resolve, reject) => {
        const tx = db.transaction(storeName, "readonly");
        const req = tx.objectStore(storeName).getAll();
        req.onsuccess = () => resolve(req.result || []);
        req.onerror = () => reject(req.error);
      });
    } finally { db.close(); }
  }
  async function mergeRecords(storeName, records, keyField) {
    const db = await openDb();
    try {
      return await new Promise((resolve, reject) => {
        const tx = db.transaction(storeName, "readwrite");
        const store = tx.objectStore(storeName);
        let inserted = 0, skipped = 0;
        for (const record of records) {
          const req = store.add(record);
          req.onsuccess = () => inserted++;
          req.onerror = event => {
            if (req.error && req.error.name === "ConstraintError") {
              skipped++; event.preventDefault(); event.stopPropagation();
            }
          };
        }
        tx.oncomplete = () => resolve({ inserted, skipped });
        tx.onerror = () => reject(tx.error || new Error("Database write failed"));
        tx.onabort = () => reject(tx.error || new Error("Database transaction aborted"));
      });
    } finally { db.close(); }
  }
  function validateBundle(bundle) {
    if (!bundle || bundle.format !== "rovex-study-bundle" || bundle.schemaVersion !== 1) throw new Error("Unsupported file. Expected rovex-study-bundle schemaVersion 1.");
    if (!Array.isArray(bundle.questions) || !Array.isArray(bundle.progress || []) || !Array.isArray(bundle.notes || [])) throw new Error("Bundle questions, progress and notes must be arrays.");
    const ids = new Set();
    for (const q of bundle.questions) {
      if (!q || typeof q.id !== "string" || !q.id.trim() || typeof q.stem !== "string" || !q.stem.trim() || !Array.isArray(q.options) || q.options.length > 20 || !q.options.every(o => typeof o === "string")) throw new Error("A question has an invalid ID, stem or options.");
      if (ids.has(q.id)) throw new Error("Duplicate question ID in import: " + q.id);
      ids.add(q.id);
      if (q.answerIndex !== undefined && q.answerIndex !== null && (!Number.isInteger(q.answerIndex) || q.answerIndex < 0 || q.answerIndex >= q.options.length)) throw new Error("Invalid answer index for question " + q.id);
      if (JSON.stringify(q).length > 250000) throw new Error("A single question record exceeds the prototype safety limit.");
    }
    for (const p of bundle.progress || []) if (!p || typeof p.questionId !== "string" || !["known", "missed", "unreviewed"].includes(p.status)) throw new Error("Invalid progress record.");
    for (const n of bundle.notes || []) if (!n || typeof n.id !== "string" || typeof n.text !== "string") throw new Error("Invalid note record.");
    return bundle;
  }
  async function refresh() {
    state.questions = await readAll("questions");
    state.progress = new Map((await readAll("progress")).map(p => [p.questionId, p]));
    $("question-count").textContent = state.questions.length;
    $("reviewed-count").textContent = [...state.progress.values()].filter(p => p.status !== "unreviewed").length;
    const subjects = [...new Set(state.questions.map(q => q.subject || "Uncategorised"))].sort((a,b) => a.localeCompare(b));
    const select = $("subject-filter"), previous = state.subject;
    select.replaceChildren(new Option("All subjects", ""));
    for (const subject of subjects) select.add(new Option(subject, subject));
    select.value = subjects.includes(previous) ? previous : "";
    state.subject = select.value;
    $("subject-count").textContent = subjects.length;
    render();
  }
  function render() {
    const qlist = $("question-list");
    qlist.replaceChildren();
    const search = state.search.trim().toLocaleLowerCase();
    const items = state.questions.filter(q => (!state.subject || (q.subject || "Uncategorised") === state.subject) && (!search || [q.stem, q.subject, q.category, ...(q.options || []), ...(q.tags || [])].join(" ").toLocaleLowerCase().includes(search)));
    $("result-count").textContent = items.length + (items.length === 1 ? " result" : " results");
    if (!items.length) {
      const empty = document.createElement("div"); empty.className = "empty panel"; empty.textContent = state.questions.length ? "No questions match these filters." : "Load the sample set or import a study bundle to begin."; qlist.append(empty); return;
    }
    const frag = document.createDocumentFragment();
    for (const q of items) frag.append(makeCard(q));
    qlist.append(frag);
  }
  function makeCard(q) {
    const card = document.createElement("article"); card.className = "question-card panel";
    const meta = document.createElement("div"); meta.className = "question-meta";
    for (const label of [q.subject || "Uncategorised", q.category || "Question", state.progress.get(q.id)?.status || "unreviewed"]) {
      const tag = document.createElement("span"); tag.className = "tag"; tag.textContent = label; meta.append(tag);
    }
    const stem = document.createElement("h3"); stem.textContent = q.stem; card.append(meta, stem);
    const options = document.createElement("ol"); options.className = "options";
    (q.options || []).forEach((option, i) => {
      const li = document.createElement("li"); li.textContent = String.fromCharCode(65 + i) + ". " + option;
      if (q.answerIndex === i) li.dataset.correct = "true";
      options.append(li);
    });
    card.append(options);
    const answer = document.createElement("div"); answer.className = "answer hidden";
    const heading = document.createElement("strong"); heading.textContent = Number.isInteger(q.answerIndex) ? "Answer: " + String.fromCharCode(65 + q.answerIndex) : "Answer";
    const explanation = document.createElement("p"); explanation.textContent = q.explanation || "No explanation included in this bundle.";
    answer.append(heading, explanation); card.append(answer);
    const actions = document.createElement("div"); actions.className = "card-actions";
    const reveal = document.createElement("button"); reveal.className = "secondary"; reveal.textContent = "Reveal answer";
    reveal.addEventListener("click", () => {
      const hidden = answer.classList.toggle("hidden");
      reveal.textContent = hidden ? "Reveal answer" : "Hide answer";
      options.querySelectorAll("li").forEach(li => li.classList.toggle("correct", !hidden && li.dataset.correct === "true"));
    });
    actions.append(reveal);
    for (const [label, status] of [["Mark known", "known"], ["Mark missed", "missed"]]) {
      const btn = document.createElement("button"); btn.className = "secondary"; btn.textContent = label;
      btn.addEventListener("click", async () => {
        const record = { questionId: q.id, status, updatedAt: new Date().toISOString() };
        await putRecord("progress", record); state.progress.set(q.id, record); await refresh();
        $("import-status").textContent = "Review saved locally for this browser.";
        $("import-status").className = "message success";
      });
      actions.append(btn);
    }
    card.append(actions); return card;
  }
  async function putRecord(storeName, record) {
    const db = await openDb();
    try { await new Promise((resolve,reject) => { const tx = db.transaction(storeName, "readwrite"); tx.objectStore(storeName).put(record); tx.oncomplete = resolve; tx.onerror = () => reject(tx.error); }); }
    finally { db.close(); }
  }
  function status(message, kind = "") { const el = $("import-status"); el.textContent = message; el.className = "message" + (kind ? " " + kind : ""); }
  async function importBundle(file) {
    if (!file) return;
    if (file.size > MAX_IMPORT_BYTES) throw new Error("File exceeds 25 MiB prototype limit. Use a smaller selected-content bundle.");
    const bundle = validateBundle(JSON.parse(await file.text()));
    const q = await mergeRecords("questions", bundle.questions, "id");
    const p = await mergeRecords("progress", bundle.progress || [], "questionId");
    const n = await mergeRecords("notes", bundle.notes || [], "id");
    await refresh();
    status("Imported " + q.inserted + " questions (" + q.skipped + " existing IDs skipped), " + p.inserted + " progress records and " + n.inserted + " notes. Existing records were not overwritten.", "success");
  }
  async function exportBundle() {
    const bundle = { format: "rovex-study-bundle", schemaVersion: 1, exportedAt: new Date().toISOString(), questions: await readAll("questions"), progress: await readAll("progress"), notes: await readAll("notes") };
    const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob), a = document.createElement("a");
    a.href = url; a.download = "rovex-study-bundle-" + new Date().toISOString().slice(0,10) + ".json"; a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    status("Export created. Store it somewhere safe; it may contain personal notes and study progress.", "success");
  }
  async function loadDemo() {
    const result = await mergeRecords("questions", demo, "id");
    await refresh();
    status("Sample set ready. Added " + result.inserted + " synthetic questions; " + result.skipped + " existing IDs skipped. No production Rovex data was accessed.", "success");
  }
  $("load-demo").addEventListener("click", () => loadDemo().catch(e => status(e.message, "error")));
  $("import-file").addEventListener("change", async event => {
    try { await importBundle(event.target.files[0]); } catch (e) { status("Import failed: " + e.message, "error"); } finally { event.target.value = ""; }
  });
  $("export-data").addEventListener("click", () => exportBundle().catch(e => status("Export failed: " + e.message, "error")));
  $("subject-filter").addEventListener("change", event => { state.subject = event.target.value; render(); });
  $("search-input").addEventListener("input", event => { state.search = event.target.value; render(); });
  $("connection-status").textContent = navigator.onLine ? "Online" : "Offline";
  window.addEventListener("online", () => $("connection-status").textContent = "Online");
  window.addEventListener("offline", () => $("connection-status").textContent = "Offline");
  if ("serviceWorker" in navigator && (location.protocol === "https:" || location.hostname === "localhost")) navigator.serviceWorker.register("./sw.js").catch(() => {});
  refresh().catch(e => status("Browser storage unavailable: " + e.message, "error"));
})();
