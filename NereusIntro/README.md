# NereusIntro 1.2.1 — Cinematic Isekai Reincarnation System

Intro isekai untuk pemain baru Nereus MC: kehidupan lama berakhir → ruang putih → bertemu **NERITH** → dunia **Nereus**.
Paper 1.21.11 · Java 21 · FancyNpcs (wajib) · Floodgate/Geyser (opsional, untuk deteksi Bedrock).

> **Status pengujian jujur:** kode sudah dikompilasi oleh CI Maven, tetapi **belum diuji di server nyata**, belum diuji di klien Bedrock.
> Jangan anggap kompatibilitas Java/Bedrock 100% sebelum menjalankan daftar uji di bawah.

## Instalasi
1. Pastikan **FancyNpcs** (2.7–2.10) terpasang. Floodgate + Geyser opsional.
2. Letakkan `NereusIntro-1.0.0.jar` di `plugins/`, restart server.
3. Atur lokasi (wajib, lihat di bawah), lalu `/intro reload`.

## Mengatur lokasi
Tanpa lokasi, intro **tidak berjalan** dan pemain **tidak diteleport** ke mana pun (pesan error jelas).
1. Bangun ruang putih (lantai putih luas, mis. beton putih, di world khusus atau area tertutup).
2. Berdiri di titik tempat pemain muncul, **hadapkan pandangan ke arah Nerith akan berdiri**, ketik `/intro setpos room`.
3. Berdiri di spawn Nereus, ketik `/intro setpos spawn` (atau isi `locations.spawn.use-world-spawn-of: "world"`).
4. `/intro test` untuk mencoba.

Contoh manual di `config.yml`:
```yaml
locations:
  room:  { world: "nereus_intro", x: 0.5, y: 100.0, z: 0.5, yaw: 180.0, pitch: 0.0 }
  spawn: { use-world-spawn-of: "", world: "world", x: 120.5, y: 72.0, z: -40.5, yaw: 0.0, pitch: 0.0 }
```
Nerith muncul `npc.position.forward` blok (default 3) di depan pemain dan menghadap pemain. Mode `absolute` memakai `locations.nerith`.

## Command
| Command | Fungsi | Permission |
|---|---|---|
| `/intro` | jalankan intro sendiri | `nereusintro.use` |
| `/intro skip` | lewati (pemain biasa: konfirmasi 2x) | `nereusintro.use` |
| `/intro test [pemain] [--bedrock\|--java]` | mode test, progres tidak berubah, kondisi dipulihkan | `nereusintro.admin` |
| `/intro play <pemain>` | jalankan intro untuk pemain lain | admin |
| `/intro reload` | muat ulang config (sesi aktif tidak terganggu) | admin |
| `/intro reset <pemain>` | hapus progres selesai/terputus | admin |
| `/intro setpos <room\|spawn\|nerith>` | simpan lokasi saat ini ke config | admin |

Jika `/intro` dipakai plugin lain, NereusIntro **tidak mengambil alih**; peringatan muncul di konsol dan alias aman `/nereusintro` (`/nintro`) tetap bekerja.

## LuckPerms
```
lp group default permission set nereusintro.use true      # sudah true secara bawaan
lp group admin   permission set nereusintro.admin true    # sudah termasuk nereusintro.use
```

## Skin "serba putih"
Plugin tidak membawa file skin. Penampilan putih dibuat dengan **armor kulit diwarnai putih** di atas skin yang dipilih.
Untuk hasil terbaik, buat skin putih di mineskin.org lalu isi `npc.skin.mode: texture`, `value`, `signature`
(atau `mode: username/url`). Skin dimuat async saat start/reload.

## Musik latar (1.1.0)
Langkah timeline `music` (`action: start|stop`, `sound`, `bedrock-sound`, `length`, `volume`) memutar suara custom dari resource pack,
diulang sesuai `length` (detik), dan selalu dihentikan saat intro selesai/skip/error/pemain keluar. Tanpa resource pack: hening, tidak error.
Volume tidak bisa di-fade oleh klien Minecraft (musik masuk/keluar langsung).

Membuat pack dari lagu (mp3/wav/m4a) — butuh `ffmpeg`:
```
python3 NereusIntro/tools/build_pack.py --whiteroom ruang_putih.mp3 --nereus nereus.mp3 --out hasil
```
Hasil: `Nereus-Intro-Java.zip`, `Nereus-Intro-Bedrock.mcpack`, dan panjang tiap lagu untuk `length:`.
- Java: gabungkan ke pack server (namespace `nereusintro`, tidak bentrok dengan `assets/nereus`), perbarui `resource-pack` / `resource-pack-sha1` di `server.properties`.
- Bedrock: taruh `.mcpack` di `plugins/Geyser-Spigot/packs/`, restart Geyser.
- Config lama tidak ditimpa saat update: salin blok `music` dari `config.yml` baru atau hapus config lama.

## Efek suara berdurasi, efek dialog, pack otomatis (1.2.0)
- Langkah `sound` punya `duration` (tick) dan `interval`: suara vanilla pendek (hujan, klakson, dengung) diulang tiap `interval` selama `duration`, lalu dihentikan.
  Hentikan lebih awal dengan `type: sound`, `action: stop`, `sound: <key yang sama>`. Semua dihentikan otomatis saat intro berakhir.
- `cinematic.dialogue-effects`: bunyi lembut + partikel cahaya tipis di sekitar Nerith setiap baris `say`, bunyi kecil saat ucapan pemain.
- `resource-pack:` plugin mengirim pack musik ke pemain Java sendiri (URL + SHA1 di config) dan menunggu sampai termuat sebelum intro mulai,
  sehingga `server.properties` tidak perlu diubah. Ditolak/gagal/timeout: intro tetap jalan tanpa musik. Pemain Bedrock tetap memakai `.mcpack` di Geyser.

## Cerita
Seluruh narasi, dialog, title, sound, dan jeda ada di `timeline:` pada `config.yml` (tanpa ubah kode). Jenis langkah:
`narrate, say, player, title, actionbar, sound, fade, teleport, npc, wait`. Teks memakai MiniMessage; `<player>` = nama pemain.
Jeda baris teks otomatis menurut panjangnya (`cinematic.pause`), atau set `wait:` per langkah.

## Keamanan dan recovery
- Satu sesi per pemain; start ganda ditolak.
- Kondisi pemain (lokasi, gamemode, terbang, kecepatan, invulnerable, health, food) disimpan ke `plugins/NereusIntro/recovery/<uuid>.yml`.
- Disconnect, kick, crash, `/reload`, atau stop server → pemain dipulihkan ke lokasi/kondisi semula (saat quit, saat plugin dimatikan, atau saat login berikutnya).
- Intro yang terputus diulang otomatis saat login (maks `recovery.max-restarts`).
- Inventory, balance, rank, level, XP, statistik **tidak pernah disentuh**.
- NPC dibuat per-pemain lewat packet FancyNpcs, tidak didaftarkan ke FancyNpcs (tidak tersimpan, tidak terlihat orang lain), selalu dihapus.
- Pengaman waktu maksimal sesi (`cinematic.max-duration-minutes`).

## Java & Bedrock
- Deteksi Bedrock: Floodgate API bila ada; jika tidak, pola UUID Floodgate.
- Title/subtitle, chat MiniMessage, teleport + rotasi, dan fade (efek Blindness) memakai fitur standar yang diterjemahkan Geyser.
- Sound: setiap langkah `sound` punya `bedrock-sound` (fallback) yang dipakai pemain Bedrock.
- Jika NPC bermasalah di Bedrock: `bedrock.spawn-npc: false` (dialog tetap tampil lewat chat), atau `npc.on-failure: abort`.

### Daftar uji (lakukan sebelum dipakai publik)
Java: 1) `/intro test` sebagai admin: Nerith terlihat tepat di depan dan menghadap Anda; 2) akun lain di dekat ruang putih tidak melihat NPC maupun Anda; 3) setelah selesai, gamemode/posisi kembali; 4) `/intro skip` di tengah; 5) logout di tengah lalu login: terpulihkan; 6) `/intro reload` saat sesi berjalan.
Bedrock (Geyser+Floodgate): ulangi 1–6, plus periksa: skin/armor putih NPC tampil (bukan Steve gelap/tak terlihat), title tampil, suara terdengar, fade gelap bekerja. Dari akun Java, `/intro test --bedrock` hanya mensimulasikan jalur sound/NPC Bedrock, **bukan** pengganti uji klien Bedrock sungguhan.
Cek juga: tidak ada NPC "nereusintro-…" tersisa di `/npc list` dan tidak ada error di konsol.

## Batasan yang diketahui
- Rendering NPC player-entity FancyNpcs di Bedrock via Geyser (skin dan armor) belum terverifikasi di sini.
- Fade memakai Blindness (gelap), bukan putih; "cahaya memenuhi ruangan" disampaikan lewat narasi, sound, dan fade.
- Struktur/dekorasi ruang putih tidak dibuat plugin; bangun sendiri.
