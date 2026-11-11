# NereusRpg

Satu plugin RPG untuk Nereus MC (Paper 1.21.11, Java + Bedrock lewat Geyser).
Menggantikan ODailyQuests dan NereusGuild. Jangan jalankan keduanya bersamaan
(perintah `/guild`, `/advrank` bentrok).

## Fitur
- Intro isekai saat pertama login, lalu wajib pilih class (`/class`).
- Class: Warrior, Assassin, Archer. Tiap class punya buff permanen berbeda dan 3 skill (`classes.yml`).
- Senjata class dipilih admin: pegang item, `/rpg admin weapon add <class>`.
  Cocok berdasarkan material + nama item (jalan juga untuk item MMOItems). Daftar kosong = semua item boleh.
- Level, XP, stat STR/DEX/INT/VIT/LUK (`/stats`), mana dengan regen.
- Skill: klik kanan pakai skill terpilih (busur: jongkok + klik kanan), jongkok + klik kiri ganti skill. Tombol F, Shift+F, dan `/skill <nomor>` juga bisa.
- Quest harian (`/quest`): 80 quest diimpor dari ODailyQuests, GET diserahkan lewat menu, reset tengah malam.
- Misi cerita berantai (`/story`, `missions.yml`).
- Rank guild F-SSS (`/rank`): GP, ujian KILL/BREAK/GET/MANUAL, hadiah naik rank.
- Semua menu punya tombol Kembali ke menu utama.
- Login harian beruntun dan pencapaian.

## Tingkat kesulitan
Diatur di `config.yml`: `quests.gp-multiplier` (hadiah GP), `quests.amount-multiplier` (jumlah tugas), `quests.per-day-limit` (maks quest per hari), GP minimal tiap rank, dan ujian (jumlah, waktu, gagal jika mati). Default dibuat berat: sekitar seminggu per rank.

## Aturan quest
Rank X boleh quest X dan semua di bawahnya, ditambah quest tantangan satu rank di atas X.
Quest BREAK tidak menghitung blok yang dipasang pemain.

## Rank di atas kepala dan TAB (terpisah dari rank donatur)
Placeholder (butuh PlaceholderAPI): `%nereusrpg_rank%` (berwarna), `%nereusrpg_rank_plain%`,
`%nereusrpg_class%`, `%nereusrpg_level%`, `%nereusrpg_gp%`, `%nereusrpg_mana%`.
Pasang di TAB, misalnya:
- tagprefix / nametag: `&8[%nereusrpg_rank%&8] `
- tabprefix atau tabsuffix sesuai selera. Rank donatur tetap dari LuckPerms.
Alternatif: meta LuckPerms `adv_rank` otomatis diisi lewat `sync-commands`,
atau `display.builtin-nametag: true` (matikan jika TAB sudah mengatur nametag).

## Admin
`/rpg admin setrank|setclass|setlevel|addxp|addgp|examdone|resetdaily|sync <pemain> ...`,
`/rpg admin weapon add|list|remove <class>`, `/rpg reload`.

## Impor ulang quest dari ODailyQuests
`python3 tools/import_odailyquests.py <folder quests> src/main/resources/quests.yml`

## Hadiah
Perintah konsol di config memakai `eco give %player% N` (EssentialsX). GP disimpan internal.
Belum diuji di server: cek hadiah item (mi give, CrazyCrates) satu per satu setelah dipasang.
