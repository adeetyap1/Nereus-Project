#!/usr/bin/env python3
"""Ubah folder quests/ ODailyQuests menjadi quests.yml NereusRpg.
Pakai: python3 import_odailyquests.py <folder quests ODailyQuests> <quests.yml keluaran>"""
import glob, os, re, sys
import yaml

src, out = sys.argv[1], sys.argv[2]
order = ["f", "e", "d", "c", "b", "a", "s", "ss", "sss"]
res = {}
for path in sorted(glob.glob(os.path.join(src, "*.yml"))):
    base = os.path.basename(path)[:-4]
    challenge = base.endswith("_tantangan")
    rank = base.replace("_tantangan", "")
    if rank not in order:
        continue
    data = yaml.safe_load(open(path, encoding="utf-8")) or {}
    for num, q in (data.get("quests") or {}).items():
        gp = 0
        cmds = []
        for c in q["reward"].get("commands", []):
            m = re.match(r"points give %player% (\d+)", c)
            if m:
                gp += int(m.group(1))
            else:
                cmds.append(c)
        desc = q.get("description", [])
        lore = []
        if any("Hadiah" in l for l in desc):
            i = [k for k, l in enumerate(desc) if "Hadiah" in l][0]
            lore = [l for l in desc[i + 1:] if l.strip()]
        qid = f"{rank}-{num}" + ("-t" if challenge else "")
        res[(order.index(rank), challenge, num)] = (qid, {
            "rank": rank.upper(), "challenge": challenge, "name": q["name"],
            "icon": q["menu_item"], "type": q["quest_type"], "target": str(q["required"]),
            "amount": int(q["required_amount"]), "gp": gp, "commands": cmds, "reward-lore": lore})
quests = {qid: v for _, (qid, v) in sorted(res.items())}
with open(out, "w", encoding="utf-8") as f:
    f.write("# Quest harian. Diimpor dari ODailyQuests, boleh diedit.\n"
            "# type: KILL (entity) | BREAK (blok) | GET (item, diserahkan)\n"
            "# gp = Guild Point. commands = perintah konsol hadiah. %player% diganti nama pemain.\n")
    yaml.safe_dump({"quests": quests}, f, allow_unicode=True, sort_keys=False, width=200)
print(len(quests), "quest ditulis")
