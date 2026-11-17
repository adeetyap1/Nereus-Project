# NerusRPG 2.0.0

RPG + **Nerus Guild** dalam satu plugin (Paper 1.21.11, Java 21). Menggantikan **NereusRpg** dan **GuildMaster**:
lepas kedua jar lama, pasang `NerusRPG-2.0.0.jar`. Data lama dimigrasi otomatis.

## Alur pemain
1. Pemain baru: status **UNREGISTERED**, Rank **NONE**, Level **0**. Tetap bisa survival; quest, skill, EXP RPG terkunci.
2. Bicara dengan **Caylin** (Guild Master, NPC FancyNpcs) → info → konfirmasi → daftar.
3. Sukses: **REGISTERED**, Rank **F**, Level **1**, lalu memilih class.
4. Naik rank lewat Level, EXP, quest, GP (+ujian opsional) hingga **S**. Tidak ada rank di atas S.
5. Admin dapat **REVOKE** (progres tidak dihapus) dan **RESTORE** (progres kembali utuh).

## Instalasi
1. Hapus `NereusRpg-*.jar` dan `GuildMaster-*.jar` dari `plugins/` (jangan hapus foldernya dulu).
2. Pasang `NerusRPG-2.0.0.jar`, restart. Migrasi berjalan sekali saat start; laporannya di `plugins/NerusRPG/logs/migration-*.txt`.
3. Opsional: PlaceholderAPI, FancyNpcs (2.7–2.10), Floodgate, LuckPerms, TAB, ItemsAdder, MythicMobs.

Struktur `plugins/NerusRPG/`: `config.yml`, `ranks.yml`, `messages.yml`, `guild-dialogue.yml`, `quests.yml`, `missions.yml`, `classes.yml`, `playerdata/` (satu file per UUID), `backups/`, `logs/`.

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

Pintasan pemain: `/quest /story /class /stats /skill /rank` (hanya anggota aktif). `nereusrpg.admin` memberi semua izin admin.

**Konflik `/gm`:** Essentials memakai `/gm` untuk gamemode. NerusRPG dimuat sebelum Essentials dan, jika `/gm` masih dipakai plugin lain, mengambil alihnya (`commands.take-over-gm: true`, tercatat di konsol). Gamemode tetap lewat `/gamemode`. Set `false` untuk membiarkan plugin lain dan pakai `/nereusrpg`.

## Placeholder (PlaceholderAPI)
`%nereusrpg_rank%` (huruf berwarna, NONE bila belum daftar), `_rank_plain`, `_rank_name`, `_rank_color`, `_rank_legacy` (warna cadangan Bedrock), `_rank_rarity`, `_level`, `_exp` (EXP di level ini), `_exp_total`, `_guild_status`, `_registered` (true/false), `_quests_completed`, `_quests_active`, plus `_class`, `_gp`, `_mana`, `_maxmana`, `_next_rank`, `_story`, dll. Berfungsi untuk REGISTERED, UNREGISTERED, REVOKED (rank NONE, level 0), dan pemain offline bila datanya ada.

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
- Quest memakai sistem quest NerusRPG (quests.yml, solo). Papan ODailyQuests dari GuildMaster 1.x tidak dibawa.
- MySQL GuildMaster hanya dibaca saat migrasi; penyimpanan kini berupa file YAML.
