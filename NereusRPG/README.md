# NereusRPG 3.0.0

RPG + **Nerus Guild** dalam satu plugin (Paper 1.21.11, Java 21). **Sebelumnya bernama NerusRPG** (dirombak besar:
Sistem Ras, 6 atribut, 78 quest, cooldown per ID quest, GUI quest 54 slot).

## Pindah dari NerusRPG 2.x (WAJIB baca)
1. **Hapus `NerusRPG-*.jar`** dari folder `plugins` (kalau dibiarkan, NereusRPG menolak menyala supaya data tidak bentrok).
2. Taruh `NereusRPG-3.0.0.jar`, restart penuh.
3. Saat pertama menyala, plugin otomatis: membuat backup zip `plugins/NereusRPG/backups/pre-v3-NerusRPG-*.zip`, lalu
   **menyalin** seluruh isi `plugins/NerusRPG` ke `plugins/NereusRPG`. Folder lama tidak dihapus (simpan sebagai cadangan).
4. Data pemain tetap utuh: level, EXP, rank, GP, progres cerita, quest aktif, riwayat.
   - **Class lama -> Ras:** warrior -> Orc, assassin -> Dark Elf, archer -> Elf (`races.class-migration` di config). Class asli disimpan
     (`legacy-class`). Pemain boleh memilih ras lain **sekali tanpa cooldown** lewat `/race`.
   - **Atribut:** DEX lama otomatis jadi AGI (jumlah poin sama). DEF baru mulai 0.
   - **Quest aktif lama:** tetap bisa diselesaikan (dimuat dari `quests-legacy.yml`), tidak ditawarkan lagi. Riwayat selesai tetap.
   - **Cooldown:** sistem lama per jenis diganti per ID quest; cooldown lama diabaikan.
5. `quests.yml` baru (78 quest) dan `races.yml` (menggantikan `classes.yml`) dibuat dari jar. File lama disimpan sebagai
   `quests-legacy.yml` dan `classes-legacy.yml`.
6. Placeholder tetap `%nereusrpg_...%` (tambahan: `race`, `race_plain`, `quests_cd_<id-quest>`).

## Ras (races.yml)
8 ras, masing-masing **5 kemampuan pasif + 1 kekurangan** (total 40 pasif + 8 kekurangan). Semua efek otomatis, vanilla (tanpa resource pack),
aman untuk Bedrock lewat Geyser. Angka tiap kemampuan bisa diubah di `races.yml` (`values`). Bonus damage ras yang mengenai pemain lain
dikali `races.pvp-scale` (0.6) supaya PvP seimbang; total pengali serangan dibatasi 0.5x-1.75x.

| Ras | Pasif | Kekurangan |
|---|---|---|
| Elf | Panah Elf, Langkah Hutan, Santapan Alam, Mata Bintang, Langkah Ringan | -2 hati darah |
| Human | Cepat Belajar (+8% EXP), Lidah Perak (+10% NR quest), Napas Kedua, Tangan Cekatan, Daya Tahan | +15% damage sihir/racun |
| Orc | Kekuatan Buas, Kulit Tebal, Haus Darah, Hati Berserk, Perut Baja | lambat, busur -25% |
| Vampire | Hisap Darah, Pemangsa Malam, Terjangan Pemburu, Regenerasi Gelap, Sanguin | Weakness di bawah matahari |
| Fairy | Seringan Bulu, Debu Peri, Tangan Hijau, Kilau Beruntung, Sentuhan Penyembuh | -3 hati, +10% damage jarak dekat |
| Dragonborn | Sisik Naga, Gigitan Naga, Raungan Naga, Karnivora, Kekuatan Purba | Slowness di biome dingin, lapar 20% lebih cepat |
| Dark Elf | Jubah Bayangan, Tikam Punggung, Bilah Racun, Penghuni Kedalaman, Elakan | -12% damage saat siang |
| Dwarf | Jantung Gunung, Pencari Bijih, Pukulan Berat, Raga Besi, Naluri Batu | jalan -8% lebih lambat |

Mekanisme: semua berbasis event (damage, kill, makan, durability, drop blok, interaksi). Hanya kemampuan lingkungan (cahaya, biome, ketinggian)
dicek oleh **satu** timer 2 detik. Skill aktif lama dipertahankan untuk Orc, Elf, dan Dark Elf (melanjutkan class lama).

## Atribut
STR (damage jarak dekat), DEF (kurangi damage diterima), VIT (HP), AGI (kecepatan jalan, crit, damage panah), INT (mana, kekuatan skill),
LUK (hadiah quest x1.5 dan atribut Luck untuk loot). Semua punya batas di `config.yml` (`stats`).

## Quest
- **13 kategori x 6 variasi = 78 quest**: Combat, Mining, Gathering, Farming, Economy, Crafting, Survival, Hunting, Building, Smithing,
  Collection, Woodcutting, Animal Husbandry. Tier **F E D C B A S** (S tertinggi). Rank X boleh ambil tier X ke bawah + satu tier di atas.
- **Tanpa batas harian dan tanpa batas jumlah quest.** Cooldown **5 jam per ID quest** (dihitung sejak diambil, bertahan setelah restart).
  Mengambil quest lain tidak memengaruhi cooldown quest lain.
- Hadiah (EXP, Nerith, GP) dari tabel tier x `weight` quest (`quests.tiers` di config). Nerith diberikan lewat `quests.nr-command`.
- Selesai = **klaim** di `/quest` (tombol Claim). Reward hanya sekali: quest dihapus dari daftar aktif sebelum hadiah diberikan.
- Tracking event: BlockBreak, EntityDeath, CraftItem, FurnaceExtract, SmithItem, EnchantItem, EntityBreed, EntityTame, PlayerShear,
  PlayerHarvestBlock, PlayerItemConsume, PlayerBedLeave, BlockPlace, InteractEntity. Statistik Minecraft untuk quest STAT. Tanpa scheduler.
- Anti-eksploit: blok buatan pemain tidak dihitung untuk BREAK; tanam/pasang di posisi yang baru dihancurkan (30 dtk) tidak dihitung;
  craft shift-click dihitung dari jumlah yang benar-benar jadi; craft bolak-balik (blok/ingot/permata) tidak dihitung; Creative/Spectator
  tidak dihitung; GET dihitung dari inventory hanya saat klaim lalu item diambil; hewan bayi tidak dihitung untuk syarat ADULT.
- **Economy** (SELL / SELL_VALUE / BUY_VALUE) membaca `PostTransactionEvent` EconomyShopGUI lewat refleksi. Tanpa EconomyShopGUI, quest Economy
  tidak maju (console memberi tahu). Belum diuji pada semua versi EconomyShopGUI.
- Tambah quest sendiri di `quests.yml` (format dan tipe dijelaskan di kepala file). Nama target salah dilaporkan di console saat dimuat.

## GUI `/quest` (54 slot)
Border hitam, separator abu-abu, ikon tiap kategori, Book (info), Clock (aktif), Emerald (selesai), Arrow (navigasi), kepala pemain (profil).
Tab: Available, Active, Completed, History. Klik quest membuka **Details** (kategori, tier, deskripsi, objective, progress + persen, hadiah,
tombol Accept / Claim / Batalkan). Hanya chest biasa sehingga jalan di Java dan Bedrock.

## Alur pemain
1. Pemain baru: status **UNREGISTERED**, Rank **NONE**, Level **0**. Tetap bisa survival; quest, skill, EXP RPG terkunci.
2. Bicara dengan **Caylin** (Guild Master, NPC FancyNpcs) → info → konfirmasi → daftar.
3. Sukses: **REGISTERED**, Rank **F**, Level **1**, lalu memilih ras.
4. Naik rank lewat Level, EXP, quest, GP (+ujian opsional) hingga **S**. Tidak ada rank di atas S.
5. Admin dapat **REVOKE** (progres tidak dihapus) dan **RESTORE** (progres kembali utuh).

## Instalasi
1. Hapus `NereusRpg-*.jar` dan `GuildMaster-*.jar` dari `plugins/` (jangan hapus foldernya dulu).
2. Pasang `NereusRPG-2.0.0.jar`, restart. Migrasi berjalan sekali saat start; laporannya di `plugins/NereusRPG/logs/migration-*.txt`.
3. Opsional: PlaceholderAPI, FancyNpcs (2.7–2.10), Floodgate, LuckPerms, TAB, ItemsAdder, MythicMobs.

Struktur `plugins/NereusRPG/`: `config.yml`, `ranks.yml`, `messages.yml`, `guild-dialogue.yml`, `quests.yml`, `missions.yml`, `classes.yml`, `playerdata/` (satu file per UUID), `backups/`, `logs/`.

## Integrasi FancyNpcs (NPC Caylin)
1. Buat NPC: `/npc create caylin`, atur skin/nama/lokasi sesuai selera.
2. Pastikan ID atau nama NPC ada di `config.yml` → `npc.ids` (bawaan: `caylin`, `guildmaster`).
3. Klik NPC membuka dialog. Tanpa FancyNpcs, admin bisa menguji dengan `/gm talk`.
Plugin tidak membuat NPC sendiri dan tidak memakai Citizens.

Dialog: Java = inventori (bawaan; `dialogue.java-view: DIALOG` untuk popup Paper), Bedrock = form Floodgate bila ada, jika tidak teks bernomor (`/gm choose N`).

## Command
`/nereusrpg` dan `/gm` adalah command yang sama (alias lain: `/rpg`, `/nerusrpg`).

| Command | Fungsi | Izin |
|---|---|---|
| `/gm` | menu Nerus Guild | `nereusrpg.use` |
| `/gm help` | bantuan | - |
| `/gm reload` | muat ulang konfigurasi | `nereusrpg.admin.reload` |
| `/gm info <pemain>` | ringkasan RPG | `nereusrpg.admin.info` |
| `/gm data <pemain>` | seluruh data | `nereusrpg.admin.data` |
| `/gm register <pemain>` | daftarkan manual | `nereusrpg.admin.register` |
| `/gm revoke <pemain>` | cabut keanggotaan | `nereusrpg.admin.revoke` |
| `/gm restore <pemain>` | pulihkan | `nereusrpg.admin.restore` |
| `/gm rank set <pemain> <rank>` | atur rank (F–S) | `nereusrpg.admin.rank` |
| `/gm rank reset <pemain> [confirm]` | reset ke F (butuh konfirmasi 30 detik) | `nereusrpg.admin.rank` |
| `/gm backup` | backup playerdata | `nereusrpg.admin.backup` |
| `/gm migrate` | migrasi data lama manual | `nereusrpg.admin.migrate` |
| `/gm talk` | uji dialog | `nereusrpg.admin.talk` |

Pintasan pemain: `/quest /story /race /stats /skill /rank` (`/class` masih jadi alias) (hanya anggota aktif). `nereusrpg.admin` memberi semua izin admin.

**Konflik `/gm`:** Essentials memakai `/gm` untuk gamemode. NereusRPG dimuat sebelum Essentials dan, jika `/gm` masih dipakai plugin lain, mengambil alihnya (`commands.take-over-gm: true`, tercatat di konsol). Gamemode tetap lewat `/gamemode`. Set `false` untuk membiarkan plugin lain dan pakai `/nereusrpg`.

## Placeholder (PlaceholderAPI)
`%nereusrpg_rank%` (huruf berwarna, NONE bila belum daftar), `_rank_plain`, `_rank_name`, `_rank_color`, `_rank_legacy` (warna cadangan Bedrock), `_rank_rarity`, `_rank_index` (F=1 … S=7, 0 bila belum daftar), `_rank_score` (rank×1.000.000.000 + EXP total, untuk leaderboard), `_level`, `_exp` (EXP di level ini), `_exp_total`, `_guild_status`, `_registered` (true/false), `_quests_completed`, `_quests_active`, plus `_class`, `_gp`, `_mana`, `_maxmana`, `_next_rank`, `_story`, dll. Berfungsi untuk REGISTERED, UNREGISTERED, REVOKED (rank NONE, level 0), dan pemain offline bila datanya ada.

## Rank F–S (`ranks.yml`)
| Rank | Warna | Rarity |
|---|---|---|
| F | #808080 | Common |
| E | #55AA55 | Uncommon |
| D | #3399FF | Rare |
| C | #00CED1 | Advanced |
| B | #AA55FF | Epic |
| A | #FF4444 | Legendary |
| S | #FFD700 → #B8860B (gradasi, tebal) | Mythical |

Syarat tiap rank (dapat diubah): `min-level`, `min-exp` (total), `min-quests`, `quest-ids`, `story-missions`, `min-kills`, `gp`, `permission`, serta `exam` opsional. Setiap rank punya `legacy-color` cadangan untuk Bedrock.

## Data dan migrasi
- Satu file per UUID di `playerdata/`, ditulis async dan atomik; username berubah tidak menghilangkan data. Saat REVOKED tidak ada data yang dihapus.
- Backup otomatis (`backup.interval-hours`, `backup.keep`) dan manual `/gm backup` ke `backups/`.
- Migrasi dari `plugins/NereusRpg` (players.yml, config, classes, quests, missions, weapons, menus) dan `plugins/GuildMaster` (SQLite/MySQL): backup dulu, data lama tidak dihapus, file pemain yang sudah ada tidak ditimpa, konflik dilaporkan. Pemain lama dengan progres tapi tanpa catatan GuildMaster otomatis REGISTERED (`migration.legacy-players`). Rank SS/SSS lama diturunkan ke S dan dilaporkan.

## Catatan
- Quest memakai sistem quest NereusRPG (quests.yml, solo). Papan ODailyQuests dari GuildMaster 1.x tidak dibawa.
- MySQL GuildMaster hanya dibaca saat migrasi; penyimpanan kini berupa file YAML.

## Top rank (hologram / scoreboard / AJLeaderboards tidak perlu)
`%nereusrpg_top_1_name%`, `%nereusrpg_top_1_rank%` (huruf berwarna), `_rank_plain`, `_level`, `_exp_total` — ganti `1` dengan 1..`top.size`.
`%nereusrpg_top_pos%` = posisi pemain sendiri. Urutan: rank S→F, lalu EXP total. Diperbarui otomatis tiap `top.refresh-seconds` (default 60 dtk), termasuk pemain offline.
