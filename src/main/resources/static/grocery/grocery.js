const GroceryAPI = {
  // --- list ---
  async addToList(items) {
    await fetch("/api/grocery/list/add", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(items)
    });
  },

  async clearList() {
    await fetch("/api/grocery/list/clear", { method: "POST" });
  },

  async grabList() {
    const res = await fetch("/api/grocery/list");
    if (!res.ok) throw new Error("Failed to load list");
    return res.json(); // array
  },

  async removeFromList(items) {
    await fetch("/api/grocery/list/remove", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(items)
    });
  },

  // --- pantry ---
  async addToPantry(items) {
    await fetch("/api/grocery/pantry/add", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(items)
    });
  },

  async removeFromPantry(items) {
    await fetch("/api/grocery/pantry/remove", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(items)
    });
  },

  async checkPantry(item) {
    const res = await fetch(`/api/grocery/pantry/has?item=${encodeURIComponent(item)}`);
    if (!res.ok) throw new Error("Failed to check pantry");
    return res.json(); // boolean
  }
};

const GroceryUI = {
  async refresh() {
    const list = await GroceryAPI.grabList();
    const listOut = document.getElementById("listOut");
    if (listOut) listOut.textContent = JSON.stringify(list, null, 2);

    // dump pantry via /dump to avoid another endpoint—remove if not needed
    const dump = await fetch("/api/grocery/dump").then(r => r.json());
    const pantryOut = document.getElementById("pantryOut");
    if (pantryOut) pantryOut.textContent = JSON.stringify(dump.grocery.pantry, null, 2);
  },

  async addListItem(inputId = "itemInput") {
    const v = (document.getElementById(inputId)?.value || "").trim();
    if (!v) return;
    await GroceryAPI.addToList([v]);
    await this.refresh();
  },

  async removeListItem(inputId = "itemInput") {
    const v = (document.getElementById(inputId)?.value || "").trim();
    if (!v) return;
    await GroceryAPI.removeFromList([v]);
    await this.refresh();
  },

  async clearList() {
    await GroceryAPI.clearList();
    await this.refresh();
  },

  async addPantryItem(inputId = "pantryInput") {
    const v = (document.getElementById(inputId)?.value || "").trim();
    if (!v) return;
    await GroceryAPI.addToPantry([v]);
    await this.refresh();
  },

  async removePantryItem(inputId = "pantryInput") {
    const v = (document.getElementById(inputId)?.value || "").trim();
    if (!v) return;
    await GroceryAPI.removeFromPantry([v]);
    await this.refresh();
  },

  async checkPantryItem(inputId = "pantryInput") {
    const v = (document.getElementById(inputId)?.value || "").trim();
    if (!v) return;
    const has = await GroceryAPI.checkPantry(v);
    // You can replace this with your own UI feedback
    alert(`${v}: ${has}`);
  }
};

// If you want auto-refresh on load, uncomment:
window.addEventListener("DOMContentLoaded", () => GroceryUI.refresh());
