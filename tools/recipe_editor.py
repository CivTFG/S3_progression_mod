"""GUI editor for science_recipes.js's recipe list.

Reads/writes the JSON array between the ===RECIPES-JSON-START/END=== markers in
kubejs_scripts/server_scripts/science_recipes.js (see that file's header comment for the
entry shape) - the array is written as strict JSON there specifically so this script can
parse and rewrite it with the stdlib json module instead of a real JS parser.

Each input row has a "Search..." button for picking an item id instead of typing it by
hand, backed by tools/item_index.json (see build_item_index.py). Run that script first
(and again whenever mods change) to build/refresh the index; without it, search still
opens but reports zero matches.

Run with:  python tools/recipe_editor.py
"""
import json
import shutil
import tkinter as tk
from pathlib import Path
from tkinter import messagebox, ttk

REPO_ROOT = Path(__file__).resolve().parent.parent
RECIPES_JS = REPO_ROOT / "kubejs_scripts" / "server_scripts" / "science_recipes.js"
PROGRESSION_JSON = REPO_ROOT / "config_files" / "s3_progression_mod" / "progression.json"
ITEM_INDEX_JSON = Path(__file__).resolve().parent / "item_index.json"
INSTANCE_TARGET = Path(
    r"C:\Users\erikp\curseforge\minecraft\Instances\TerraFirmaGreg-Modern"
    r"\kubejs\server_scripts\s3_progression_mod\science_recipes.js"
)

START_MARKER = "// ===RECIPES-JSON-START===\nconst SCIENCE_RECIPES = "
END_MARKER = "\n// ===RECIPES-JSON-END==="

GTCEU_MACHINES = [
    "assembler", "macerator", "mixer", "autoclave", "extruder",
    "arc_furnace", "circuit_assembler", "chemical_reactor", "large_chemical_reactor",
    "assembly_line", "electric_furnace", "bender", "cutter", "polarizer",
]
# Confirmed against com.simibubi.create.AllRecipeTypes in create-1.20.1-6.0.8.jar.
# "mechanical_crafting" (shaped grid) and "sequenced_assembly" (multi-step) are left out -
# they don't fit the flat inputs/outputs shape this tool (and the generator) assumes.
CREATE_MACHINES = [
    "crushing", "milling", "mixing", "compacting", "pressing", "cutting",
    "splashing", "haunting", "deploying", "filling", "emptying", "item_application",
]
MACHINES = ["crafting_table"] + GTCEU_MACHINES + CREATE_MACHINES
TIERS = ["ULV", "LV", "MV", "HV", "EV", "IV", "LUV", "ZPM", "UV", "UHV"]
HEAT_LEVELS = ["", "heated", "superheated"]

# Reference only, shown as a hint in the editor - Create's stress (SU) cost is a fixed
# property of the machine block, not something a recipe specifies, so it's never written
# into the recipe JSON. Typical Create defaults; addons in this pack (createaddition,
# createhorsepower, etc.) may retune them, so confirm in-game if it matters.
CREATE_MACHINE_STRESS = {
    "crushing": "4 SU per Crushing Wheel (8 SU for the pair)",
    "milling": "4 SU (Millstone)",
    "mixing": "4 SU (Mechanical Mixer, plus the Basin it sits on)",
    "compacting": "4 SU (Mechanical Press, compacting mode)",
    "pressing": "4 SU (Mechanical Press)",
    "cutting": "2 SU (Mechanical Saw)",
    "splashing": "0 SU (just needs a body of water)",
    "haunting": "0 SU (just needs soul sand/soil nearby)",
    "deploying": "4 SU (Deployer)",
    "filling": "2 SU (Spout)",
    "emptying": "2 SU (Item Drain)",
    "item_application": "4 SU (Deployer)",
}


def load_progression():
    data = json.loads(PROGRESSION_JSON.read_text(encoding="utf-8"))
    ages = [t["key"] for t in data["tiers"]]
    categories = [c.upper() for c in data["categories"]]
    return ages, categories


def load_item_index():
    if not ITEM_INDEX_JSON.exists():
        return []
    return [tuple(entry) for entry in json.loads(ITEM_INDEX_JSON.read_text(encoding="utf-8"))]


def read_recipes():
    text = RECIPES_JS.read_text(encoding="utf-8")
    start = text.index(START_MARKER) + len(START_MARKER)
    end = text.index(END_MARKER, start)
    return json.loads(text[start:end])


def format_recipes(recipes):
    lines = [json.dumps(r, separators=(", ", ": ")) for r in recipes]
    return "[\n  " + ",\n  ".join(lines) + "\n]" if lines else "[]"


def write_recipes(recipes):
    text = RECIPES_JS.read_text(encoding="utf-8")
    start = text.index(START_MARKER) + len(START_MARKER)
    end = text.index(END_MARKER, start)
    new_text = text[:start] + format_recipes(recipes) + text[end:]
    RECIPES_JS.write_text(new_text, encoding="utf-8")


class ItemPickerDialog(tk.Toplevel):
    """Modal search-as-you-type picker over tools/item_index.json (see
    build_item_index.py). Matches the query's words against both the item id and its
    display name, so e.g. "cu ingot" finds "gtceu:cupronickel_ingot" / "Cupronickel Ingot"."""

    MAX_RESULTS = 200

    def __init__(self, parent, item_index):
        super().__init__(parent)
        self.title("Search items")
        self.geometry("640x420")
        self.result = None
        self.item_index = item_index
        self._matches = []

        frame = ttk.Frame(self, padding=8)
        frame.pack(fill="both", expand=True)

        self.query_var = tk.StringVar()
        entry = ttk.Entry(frame, textvariable=self.query_var)
        entry.pack(fill="x")
        entry.bind("<KeyRelease>", lambda e: self._refresh())
        entry.bind("<Return>", lambda e: self._select_first())
        entry.focus_set()

        list_frame = ttk.Frame(frame)
        list_frame.pack(fill="both", expand=True, pady=(6, 0))
        scrollbar = ttk.Scrollbar(list_frame)
        scrollbar.pack(side="right", fill="y")
        self.listbox = tk.Listbox(list_frame, yscrollcommand=scrollbar.set, font=("Consolas", 10))
        self.listbox.pack(side="left", fill="both", expand=True)
        scrollbar.config(command=self.listbox.yview)
        self.listbox.bind("<Double-1>", lambda e: self._select())

        self.status_var = tk.StringVar()
        ttk.Label(frame, textvariable=self.status_var, foreground="#666").pack(anchor="w", pady=(4, 0))

        button_row = ttk.Frame(frame)
        button_row.pack(pady=(6, 0))
        ttk.Button(button_row, text="Select", command=self._select).grid(row=0, column=0, padx=4)
        ttk.Button(button_row, text="Cancel", command=self.destroy).grid(row=0, column=1, padx=4)

        self._refresh()
        self.grab_set()
        self.transient(parent)

    def _refresh(self):
        query = self.query_var.get().strip().lower()
        self.listbox.delete(0, "end")

        if not self.item_index:
            self.status_var.set("No item index found - run tools/build_item_index.py first.")
            self._matches = []
            return

        if query:
            terms = query.split()
            found = [
                entry for entry in self.item_index
                if all(term in f"{entry[0]} {entry[1]}".lower() for term in terms)
            ]
        else:
            found = self.item_index

        self._matches = found[: self.MAX_RESULTS]
        for item_id, name in self._matches:
            self.listbox.insert("end", f"{name}    [{item_id}]")

        if len(found) > self.MAX_RESULTS:
            self.status_var.set(f"{len(found)} matches, showing first {self.MAX_RESULTS} - keep typing to narrow it down")
        else:
            self.status_var.set(f"{len(found)} match{'es' if len(found) != 1 else ''}")

    def _select(self):
        selection = self.listbox.curselection()
        if not selection:
            return
        self.result = self._matches[selection[0]][0]
        self.destroy()

    def _select_first(self):
        if self._matches:
            self.result = self._matches[0][0]
            self.destroy()


class RecipeDialog(tk.Toplevel):
    """Modal add/edit form for one recipe entry."""

    def __init__(self, parent, ages, categories, item_index, recipe=None):
        super().__init__(parent)
        self.item_index = item_index
        self.title("Edit recipe" if recipe else "Add recipe")
        self.resizable(False, False)
        self.result = None
        self.input_rows = []

        recipe = recipe or {}
        form = ttk.Frame(self, padding=10)
        form.grid(row=0, column=0, sticky="nsew")

        self.age_var = tk.StringVar(value=recipe.get("age", ages[0]))
        self.category_var = tk.StringVar(value=recipe.get("category", categories[0]))
        self.machine_var = tk.StringVar(value=recipe.get("machine", "crafting_table"))
        self.tier_var = tk.StringVar(value=recipe.get("tier", ""))
        self.duration_var = tk.StringVar(value=str(recipe.get("duration", 100)))
        self.heat_var = tk.StringVar(value=recipe.get("heat", ""))
        self.output_var = tk.StringVar(value=str(recipe.get("output", 1)))
        self.stress_hint_var = tk.StringVar(value="")

        row = 0
        ttk.Label(form, text="Age").grid(row=row, column=0, sticky="w")
        ttk.Combobox(form, textvariable=self.age_var, values=ages, width=20, state="readonly").grid(row=row, column=1, sticky="w")
        row += 1
        ttk.Label(form, text="Category").grid(row=row, column=0, sticky="w")
        ttk.Combobox(form, textvariable=self.category_var, values=categories, width=20, state="readonly").grid(row=row, column=1, sticky="w")
        row += 1
        ttk.Label(form, text="Machine").grid(row=row, column=0, sticky="w")
        machine_box = ttk.Combobox(form, textvariable=self.machine_var, values=MACHINES, width=20)
        machine_box.grid(row=row, column=1, sticky="w")
        machine_box.bind("<<ComboboxSelected>>", lambda e: self._update_stress_hint())
        machine_box.bind("<KeyRelease>", lambda e: self._update_stress_hint())
        row += 1
        ttk.Label(form, textvariable=self.stress_hint_var, foreground="#666").grid(row=row, column=1, sticky="w")
        row += 1
        ttk.Label(form, text="Tier (GTCEU machines only)").grid(row=row, column=0, sticky="w")
        ttk.Combobox(form, textvariable=self.tier_var, values=TIERS, width=20).grid(row=row, column=1, sticky="w")
        row += 1
        ttk.Label(form, text="Duration in ticks (GTCEU/Create machines only)").grid(row=row, column=0, sticky="w")
        ttk.Spinbox(form, textvariable=self.duration_var, from_=1, to=100000, width=18).grid(row=row, column=1, sticky="w")
        row += 1
        ttk.Label(form, text="Heat (Create mixing/compacting only)").grid(row=row, column=0, sticky="w")
        ttk.Combobox(form, textvariable=self.heat_var, values=HEAT_LEVELS, width=20).grid(row=row, column=1, sticky="w")
        row += 1
        ttk.Label(form, text="Science items produced").grid(row=row, column=0, sticky="w")
        ttk.Spinbox(form, textvariable=self.output_var, from_=1, to=64, width=18).grid(row=row, column=1, sticky="w")
        row += 1

        ttk.Label(form, text="Inputs").grid(row=row, column=0, sticky="nw", pady=(8, 0))
        self.inputs_frame = ttk.Frame(form)
        self.inputs_frame.grid(row=row, column=1, sticky="w", pady=(8, 0))
        row += 1

        for entry in recipe.get("inputs", [{"item": "", "count": 1}]):
            self._add_input_row(entry.get("item", ""), entry.get("count", 1))
        if not recipe.get("inputs"):
            pass

        ttk.Button(form, text="+ Add input", command=lambda: self._add_input_row("", 1)).grid(row=row, column=1, sticky="w")
        row += 1

        button_row = ttk.Frame(form)
        button_row.grid(row=row, column=0, columnspan=2, pady=(12, 0))
        ttk.Button(button_row, text="OK", command=self._on_ok).grid(row=0, column=0, padx=4)
        ttk.Button(button_row, text="Cancel", command=self.destroy).grid(row=0, column=1, padx=4)

        self._update_stress_hint()
        self.grab_set()
        self.transient(parent)

    def _update_stress_hint(self):
        machine = self.machine_var.get().strip()
        self.stress_hint_var.set(CREATE_MACHINE_STRESS.get(machine, ""))

    def _add_input_row(self, item, count):
        row_index = len(self.input_rows)
        item_var = tk.StringVar(value=item)
        count_var = tk.StringVar(value=str(count))
        row_frame = ttk.Frame(self.inputs_frame)
        row_frame.grid(row=row_index, column=0, sticky="w", pady=2)
        ttk.Entry(row_frame, textvariable=item_var, width=32).grid(row=0, column=0, padx=(0, 4))
        ttk.Button(row_frame, text="Search...", command=lambda: self._search_item(item_var)).grid(row=0, column=1, padx=(0, 4))
        ttk.Label(row_frame, text="x").grid(row=0, column=2)
        ttk.Spinbox(row_frame, textvariable=count_var, from_=1, to=64, width=4).grid(row=0, column=3, padx=4)
        remove_button = ttk.Button(row_frame, text="Remove", command=lambda: self._remove_input_row(row_frame))
        remove_button.grid(row=0, column=4, padx=4)
        self.input_rows.append((row_frame, item_var, count_var))

    def _search_item(self, item_var):
        dialog = ItemPickerDialog(self, self.item_index)
        self.wait_window(dialog)
        if dialog.result:
            item_var.set(dialog.result)

    def _remove_input_row(self, row_frame):
        self.input_rows = [r for r in self.input_rows if r[0] is not row_frame]
        row_frame.destroy()

    def _on_ok(self):
        age = self.age_var.get().strip()
        category = self.category_var.get().strip()
        machine = self.machine_var.get().strip()
        if not age or not category or not machine:
            messagebox.showerror("Missing fields", "Age, category and machine are required.", parent=self)
            return

        inputs = []
        for _, item_var, count_var in self.input_rows:
            item = item_var.get().strip()
            if not item:
                continue
            try:
                count = int(count_var.get())
            except ValueError:
                messagebox.showerror("Bad input count", f'"{item}" has a non-numeric count.', parent=self)
                return
            inputs.append({"item": item, "count": count})
        if not inputs:
            messagebox.showerror("No inputs", "A recipe needs at least one input item.", parent=self)
            return

        try:
            output = int(self.output_var.get())
        except ValueError:
            messagebox.showerror("Bad output", "Output must be a number.", parent=self)
            return

        recipe = {"age": age, "category": category, "machine": machine, "output": output, "inputs": inputs}

        if machine in GTCEU_MACHINES:
            tier = self.tier_var.get().strip()
            if not tier:
                messagebox.showerror("Missing tier", f'Machine "{machine}" needs a tier (e.g. LV, HV).', parent=self)
                return
            try:
                duration = int(self.duration_var.get())
            except ValueError:
                messagebox.showerror("Bad duration", "Duration must be a number of ticks.", parent=self)
                return
            recipe["tier"] = tier
            recipe["duration"] = duration
        elif machine in CREATE_MACHINES:
            try:
                duration = int(self.duration_var.get())
            except ValueError:
                messagebox.showerror("Bad duration", "Duration must be a number of ticks.", parent=self)
                return
            recipe["duration"] = duration
            heat = self.heat_var.get().strip()
            if heat:
                recipe["heat"] = heat
        elif machine != "crafting_table":
            messagebox.showwarning(
                "Unknown machine",
                f'"{machine}" isn\'t a known crafting_table/GTCEU/Create machine id - '
                "the recipe will still be saved, but science_recipes.js will skip it "
                "with a console error until it's added there.",
                parent=self,
            )

        self.result = recipe
        self.destroy()


class RecipeEditorApp(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("S3 Progression Mod - Science Recipe Editor")
        self.geometry("900x500")

        self.ages, self.categories = load_progression()
        self.recipes = read_recipes()
        self.item_index = load_item_index()

        columns = ("machine", "tier", "output", "inputs")
        self.tree = ttk.Treeview(self, columns=columns, show="tree headings")
        self.tree.heading("#0", text="Age / Category")
        self.tree.column("#0", width=220, anchor="w")
        for col, width, heading in zip(columns, (130, 60, 70, 420), ("Machine", "Tier", "Output", "Inputs")):
            self.tree.heading(col, text=heading)
            self.tree.column(col, width=width, anchor="w")
        self.tree.grid(row=0, column=0, columnspan=6, sticky="nsew", padx=8, pady=8)
        self.tree.bind("<Double-1>", lambda e: self._edit_selected())

        # Age nodes default open, category nodes default closed; only remembers explicit
        # user toggles so collapsing/expanding survives a tree rebuild after Add/Edit/etc.
        self._node_open_state = {}
        self.tree.bind("<<TreeviewOpen>>", lambda e: self._node_open_state.__setitem__(self.tree.focus(), True))
        self.tree.bind("<<TreeviewClose>>", lambda e: self._node_open_state.__setitem__(self.tree.focus(), False))

        self.grid_rowconfigure(0, weight=1)
        self.grid_columnconfigure(0, weight=1)

        button_bar = ttk.Frame(self)
        button_bar.grid(row=1, column=0, columnspan=6, pady=(0, 8))
        ttk.Button(button_bar, text="Add", command=self._add).grid(row=0, column=0, padx=4)
        ttk.Button(button_bar, text="Edit", command=self._edit_selected).grid(row=0, column=1, padx=4)
        ttk.Button(button_bar, text="Duplicate", command=self._duplicate_selected).grid(row=0, column=2, padx=4)
        ttk.Button(button_bar, text="Delete", command=self._delete_selected).grid(row=0, column=3, padx=4)
        ttk.Button(button_bar, text="Move up", command=lambda: self._move(-1)).grid(row=0, column=4, padx=4)
        ttk.Button(button_bar, text="Move down", command=lambda: self._move(1)).grid(row=0, column=5, padx=4)
        ttk.Button(button_bar, text="Save", command=self._save).grid(row=0, column=6, padx=(24, 4))
        ttk.Button(button_bar, text="Save && deploy to instance", command=self._save_and_deploy).grid(row=0, column=7, padx=4)

        index_note = f"{len(self.item_index)} items indexed" if self.item_index else "no item index - run tools/build_item_index.py"
        self.status_var = tk.StringVar(value=f"Loaded {len(self.recipes)} recipes from {RECIPES_JS} ({index_note})")
        ttk.Label(self, textvariable=self.status_var).grid(row=2, column=0, columnspan=6, sticky="w", padx=8, pady=(0, 8))

        self._refresh_tree()

    def _is_open(self, node_id, default):
        return self._node_open_state.get(node_id, default)

    def _refresh_tree(self):
        selected = self._selected_index()
        self.tree.delete(*self.tree.get_children())

        groups = [(age, category) for age in self.ages for category in self.categories]
        # Any recipe with an age/category outside progression.json (e.g. a typo, or the
        # config changed since this recipe was written) still gets shown, not dropped.
        leftover_pairs = sorted({(r["age"], r["category"]) for r in self.recipes} - set(groups))
        groups += leftover_pairs

        for age, category in groups:
            indices = [i for i in range(len(self.recipes))
                       if self.recipes[i]["age"] == age and self.recipes[i]["category"] == category]
            if not indices:
                continue

            age_node = f"age:{age}"
            if not self.tree.exists(age_node):
                self.tree.insert("", "end", iid=age_node, text=age, open=self._is_open(age_node, True))

            cat_node = f"{age_node}/cat:{category}"
            self.tree.insert(age_node, "end", iid=cat_node, text=f"{category.title()} ({len(indices)})",
                              open=self._is_open(cat_node, False))

            for index in indices:
                recipe = self.recipes[index]
                inputs_summary = ", ".join(f'{i["count"]}x {i["item"]}' for i in recipe["inputs"])
                self.tree.insert(cat_node, "end", iid=str(index), values=(
                    recipe["machine"], recipe.get("tier", ""), recipe.get("output", 1), inputs_summary,
                ))

        if selected is not None and self.tree.exists(str(selected)):
            self.tree.selection_set(str(selected))

    def _selected_index(self):
        selection = self.tree.selection()
        if not selection or not selection[0].isdigit():
            return None
        return int(selection[0])

    def _add(self):
        dialog = RecipeDialog(self, self.ages, self.categories, self.item_index)
        self.wait_window(dialog)
        if dialog.result:
            self.recipes.append(dialog.result)
            self._refresh_tree()

    def _edit_selected(self):
        index = self._selected_index()
        if index is None:
            return
        dialog = RecipeDialog(self, self.ages, self.categories, self.item_index, recipe=self.recipes[index])
        self.wait_window(dialog)
        if dialog.result:
            self.recipes[index] = dialog.result
            self._refresh_tree()

    def _duplicate_selected(self):
        index = self._selected_index()
        if index is None:
            return
        self.recipes.insert(index + 1, json.loads(json.dumps(self.recipes[index])))
        self._refresh_tree()

    def _delete_selected(self):
        index = self._selected_index()
        if index is None:
            return
        if messagebox.askyesno("Delete recipe", "Delete the selected recipe?"):
            del self.recipes[index]
            self.tree.selection_remove(*self.tree.selection())
            self._refresh_tree()

    def _move(self, offset):
        index = self._selected_index()
        if index is None:
            return
        # Moves within the recipe's own age+category group (not raw adjacent list
        # indices) - otherwise this could be a no-op that does nothing visible, since the
        # tree always groups by age/category regardless of underlying list order.
        recipe = self.recipes[index]
        group = [i for i, r in enumerate(self.recipes)
                 if r["age"] == recipe["age"] and r["category"] == recipe["category"]]
        position = group.index(index) + offset
        if 0 <= position < len(group):
            target = group[position]
            self.recipes[index], self.recipes[target] = self.recipes[target], self.recipes[index]
            self._refresh_tree()
            self.tree.selection_set(str(target))
            self.tree.see(str(target))
            self.tree.selection_set(str(target))

    def _save(self):
        write_recipes(self.recipes)
        self.status_var.set(f"Saved {len(self.recipes)} recipes to {RECIPES_JS}")

    def _save_and_deploy(self):
        write_recipes(self.recipes)
        if not INSTANCE_TARGET.parent.exists():
            messagebox.showerror("Instance not found", f"Couldn't find {INSTANCE_TARGET.parent}")
            return
        shutil.copyfile(RECIPES_JS, INSTANCE_TARGET)
        self.status_var.set(
            f"Saved {len(self.recipes)} recipes and deployed to {INSTANCE_TARGET}. "
            "Run /kubejs reload_server in-game to apply."
        )


if __name__ == "__main__":
    RecipeEditorApp().mainloop()
