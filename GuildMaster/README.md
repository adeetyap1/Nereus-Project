# GuildMaster

Antarmuka **Adventurer Guild** untuk Nereus MC. GuildMaster hanya menjadi pintu masuk: pendaftaran, dialog NPC, dan menu.
Quest, EXP, rank F sampai SSS, dan hadiah tetap milik **Nereus RPG**. GuildMaster tidak menyimpan data progres.

Paper 1.21.11, Java 21. Java dan Bedrock (Geyser).

## Instalasi

1. Pasang `NereusRpg-1.1.0.jar` (atau lebih baru) dan `GuildMaster-1.0.0.jar` di `plugins/`.
2. Pasang **FancyNpcs versi 2.7 sampai 2.10** (Java 21). Versi 2.11 butuh Java 25 dan tidak cocok dengan server Java 21.
3. Opsional: **Floodgate** (form Bedrock), **PlaceholderAPI**, **LuckPerms**.
4. Restart server. Folder `plugins/GuildMaster/` terbentuk: `config.yml`, `messages.yml`, `dialogue.yml`, `gui/*.yml`, `data/`.
5. Buat NPC: `/npc create guildmaster` (FancyNpcs), atur skin dan posisi.
6. Pastikan ID/nama NPC ada di `config.yml` bagian `npc.ids` (bawaan: `guildmaster`), lalu `/guildmaster reload`.
7. Cek `/guildmaster info`: Nereus RPG dan FancyNpcs harus "terhubung".

Jika FancyNpcs atau Nereus RPG tidak ada, plugin tetap hidup dan memberi peringatan jelas di console.

## Cara kerja

- Klik NPC: muncul **dialog** (bukan menu). Pemain belum terdaftar melihat Selamat datang, Daftar, Informasi, Tutup. Pendaftaran meminta konfirmasi.
- Setelah daftar: tampil status, rank, tanggal; tombol **Buka Layanan Guild** membuka menu.
- Menu: Profil, Papan Quest (detail, syarat, hadiah, ambil), Quest Aktif (progres, serahkan, batalkan), Riwayat, Informasi.
- Java: popup native Paper (Dialog API). Bedrock: form Floodgate. Bedrock tanpa Floodgate: teks bernomor dan perintah `/guildmaster choose <nomor>`.
- Semua klik menu dibatalkan, divalidasi ulang terhadap status registrasi, dan diberi jeda. Tombol dialog lama tidak bisa dipakai ulang.
- Pendaftaran satu kali: kunci utama UUID di database, plus penjaga balapan di aplikasi.

## Perintah dan permission

| Perintah | Fungsi | Permission |
|---|---|---|
| `/guildmaster talk` | Buka dialog (admin, atau semua jika `commands.player-direct-access: true`) | `guildmaster.use` |
| `/guildmaster menu` | Buka menu guild (pemain terdaftar) | `guildmaster.use` |
| `/guildmaster reload` | Muat ulang semua config | `guildmaster.admin.reload` |
| `/guildmaster info` | Status database, RPG, FancyNpcs, Floodgate | `guildmaster.admin.reload` |
| `/guildmaster register <pemain>` | Daftarkan paksa (abaikan syarat rank) atau pulihkan yang dicabut | `guildmaster.admin.register` |
| `/guildmaster unregister <pemain>` | Cabut akses. Catatan tetap ada dan pemain tidak bisa mendaftar sendiri lagi | `guildmaster.admin.register` |
| `/guildmaster reset <pemain>` | Hapus catatan sepenuhnya, bisa mendaftar lagi dari awal | `guildmaster.admin.reset` |

`guildmaster.use` default semua pemain. `guildmaster.admin` (default op) mencakup ketiga permission admin.
Contoh LuckPerms: `lp group staff permission set guildmaster.admin true`.
Alias: `/gmaster`.

Placeholder PlaceholderAPI: `%guildmaster_registered%`, `%guildmaster_status%`, `%guildmaster_since%`.

## Konfigurasi

- `config.yml`: ID NPC, jenis klik, jarak, rank minimum pendaftaran, database (sqlite atau mysql), format tanggal.
- `dialogue.yml`: seluruh teks dan tombol dialog, per node. Aksi tombol: `goto:<node>`, `back`, `register`, `menu`, `close`.
- `messages.yml`: pesan chat.
- `gui/main.yml`, `profile.yml`, `info.yml`, `quest-board.yml`, `active-quest.yml`, `quest-history.yml`: judul, slot, material, nama, lore, filler.

## Panduan integrasi Nereus RPG

GuildMaster membaca RPG lewat interface `RpgAdapter` (`rpg/RpgAdapter.java`). Implementasi `NereusRpgAdapter`
hanya memakai method publik NereusRpg yang memang ada (diverifikasi dari source):

| Kebutuhan | Pemanggilan di NereusRpg |
|---|---|
| Rank pemain, level, XP, class, GP, statistik | `Core.profile(player)`, `Core.rank(i)`, `Core.xpNeeded`, `Core.clazz`, field `Profile` |
| Indeks rank dari kunci (syarat F) | `Core.rankIndex("F")` |
| Daftar quest yang boleh | `Core.tracker.eligible(profile)` |
| Quest aktif dan progres | `Profile.active`, `Tracker.count` (quest kumpul item) |
| Ambil quest | `Tracker.accept(player, id)` |
| Serahkan quest item | `Tracker.turnInQuest(player, id)` |
| Batalkan | `Tracker.abandon(player, id)` |
| Misi cerita aktif | `Tracker.mission(profile)` |
| Hadiah | Dibagikan RPG otomatis saat quest selesai. Tampilan hadiah dari `Defs.Quest.lore()` dan `Tracker.questXp` |
| Riwayat | `Profile.history` (ditambahkan di NereusRpg 1.1.0, 50 entri terakhir) |

Catatan: "klaim reward" tidak ada sebagai langkah terpisah di RPG. Quest otomatis memberi hadiah saat selesai;
quest bertipe kumpul item diselesaikan lewat tombol Serahkan.

Untuk memakai sistem RPG lain, buat implementasi `RpgAdapter` baru dan pilih di `GuildMaster.connectRpg()`.

## Hal yang perlu dites di server

Dibangun dan lolos compile di CI, tetapi belum dijalankan di server Paper sungguhan. Uji: klik NPC (Java), form di Bedrock
(dengan dan tanpa Floodgate), pendaftaran ganda, rank ditolak, restart server (data tetap), dan menu di dua akun.
Cakupan versi FancyNpcs tertentu perlu dicek karena API-nya dikompilasi terhadap 2.9.2.

## Di luar lingkup tahap ini

Party, team quest, shared progress, pembagian reward, dan dungeon sengaja tidak ada.
