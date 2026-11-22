# NereusRelics 1.2.0 — Legendary Weapon Altar Event

Plugin event untuk Paper 1.21.11 (Java 21). Admin membuat **satu altar** yang menampilkan senjata legendaris melayang.
Pemain memberi persembahan dengan **melempar item** ke area altar. Siapa pun yang **pertama** melengkapi seluruh
persembahannya (kontribusi per pemain, tidak pernah digabung) memulai ritual dan memperoleh senjata. Setelah klaim,
altar otomatis nonaktif (state `CLAIMED`) sampai admin melakukan `/altar reset`.

## Instalasi
1. Upload `NereusRelics-1.2.0.jar` ke folder `plugins/`, restart server.
2. `plugins/NereusRelics/config.yml` dan `relics.db` (SQLite) dibuat otomatis. Tidak perlu dependency lain (driver SQLite bawaan Paper).
3. Pastikan kamu punya permission `nereusrelics.admin` (default: OP).

## Cara membuat event (di dalam game)
```
/altar create naga
/altar setpos naga                 (berdiri di tempat altar)
/altar setweapon naga              (pegang senjata di tangan)
/altar setname naga &6&lPedang Naga Kuno
/altar setlore naga &7Ditempa di api naga|&7Hanya satu yang ada
/altar additem naga 32             (pegang item persembahan, jumlah 32)
/altar additem naga 1 exact        (item khusus/custom dicocokkan persis)
/altar setduration naga 8
/altar setannouncement naga PUBLIC
/altar activate naga
```

## Perintah (permission `nereusrelics.admin`, alias `/relicaltar`)
| Perintah | Fungsi |
|---|---|
| `/altar create <id>` | Buat altar (huruf kecil, angka, `-`, `_`) |
| `/altar setpos <id>` | Posisi = tempatmu berdiri (pusat blok) |
| `/altar setweapon <id>` | Senjata = item di tangan utama (tidak berkurang) |
| `/altar additem <id> [jumlah] [material\|exact]` | Tambah persembahan dari item di tangan. Otomatis: item polos = `material`, item bernama/lore/enchant/PDC/custom = `exact` |
| `/altar removeitem <id> <no>` | Hapus persembahan nomor tertentu |
| `/altar setamount <id> <no> <jumlah>` | Ubah jumlah |
| `/altar setname <id> <nama>` | Nama senjata (`&` warna dan `&#RRGGBB`) |
| `/altar setlore <id> <baris\|baris>` | Deskripsi (dipakai di item dan hologram) |
| `/altar setduration <id> <detik>` | Durasi ritual 1–120 detik |
| `/altar setannouncement <id> <PUBLIC\|HIDDEN\|SECRET\|ADMIN> [teks]` | Mode lokasi di pengumuman |
| `/altar set <id> <kunci> <nilai>` | Per altar: `ambient-particle`, `ambient-sound`, `ritual-particle`, `ritual-sound`, `claim-sound`, `offer-sound`, `offer-radius`, `hologram-height`, `weapon-height` (nilai `-` = default config) |
| `/altar activate <id>` | Aktifkan + umumkan (hanya satu altar boleh berjalan) |
| `/altar deactivate <id>` | Nonaktifkan; semua persembahan dikembalikan |
| `/altar reset <id>` | `CLAIMED`/`INACTIVE` → `INACTIVE` bersih (senjata baru bisa diklaim lagi) |
| `/altar remove <id>` | Hapus altar (refund bila sedang aktif) |
| `/altar info <id>`, `/altar list`, `/altar reload` | Informasi |

Pengeditan (posisi, senjata, item, nama, dll.) **diblokir saat altar ACTIVE/RITUAL**; nonaktifkan dulu.

## Mode pengumuman lokasi
- `PUBLIC`: dunia + koordinat X/Z.
- `HIDDEN`: hanya wilayah kasar (arah dari spawn), tanpa koordinat.
- `SECRET`: tanpa informasi lokasi.
- `ADMIN`: teks bebas dari `/altar setannouncement <id> ADMIN Baris1|Baris2` (kosong = tidak ada lokasi).

## Alur dan keamanan
- **Persembahan**: item yang dilempar pemain ke area altar dipindai tiap 10 tick (hanya saat altar ACTIVE). Hanya item dengan pelempar pemain yang dihitung; hanya melalui pelempar itu. Jumlah berlebih dikembalikan ke pelempar (item sisa tidak dikonsumsi). Pemain Creative/Spectator tidak bisa memberi persembahan (anti item tak terbatas; ubah `offering.allow-creative`).
- **Klaim atomik**: transisi `ACTIVE → RITUAL → CLAIMED` memakai metode `synchronized` pada objek altar; hanya satu pemain bisa memegang kunci klaim dan hanya satu kali bisa menyelesaikannya. Pemain lain yang juga lengkap pada tick yang sama tidak kehilangan apa pun: item mereka dikembalikan.
- **Anti-duplikasi senjata**: senjata dan refund dicatat di tabel `deliveries` dalam **satu transaksi** bersama perubahan state altar. Saat diberikan, baris ditandai `delivered` secara sinkron **sebelum** item masuk inventory; bila pemberian gagal, tanda dibatalkan. Pemain offline/mati saat klaim menerima item saat login/respawn. Inventory penuh → item dijatuhkan di kaki, hanya bisa diambil pemiliknya.
- **Proteksi item persembahan**: tidak bisa di-merge, diambil hopper, diambil pemain lain, atau despawn selama berada di area altar.
- **Proteksi area**: blok break/place, ledakan, piston, api, aliran cairan, dan enderman/falling block dicegah di radius `protection.*` selama altar berjalan (admin dengan permission melewati).
- **Anti-ClearLag**: penghapusan entity display altar oleh plugin lain terdeteksi lewat `EntityRemoveEvent` dan entity dibuat ulang (debounce, maks `entity-recovery.max-respawns-per-10s`). Chunk yang dimuat diperiksa: duplikat dan entity yatim dihapus. Ada validasi ringan tiap 60 detik hanya saat altar berjalan. Item persembahan yang dihapus plugin lain dikembalikan ke pelemparnya.
- **Persistensi**: SQLite (WAL, `synchronous=FULL`), satu thread database. Tulis progres bersifat asinkron (tidak memblokir server); operasi kritis (aktivasi, mulai ritual, klaim, pengiriman) sinkron dalam transaksi. Ritual yang terputus restart dilanjutkan (hitung mundur diulang dari awal).

## Scene sinematik klaim (1.1.0)
Setelah ritual selesai dan klaim tersimpan di database, scene berjalan (default 9 detik): hologram hilang, senjata naik dan berputar
makin cepat, heliks partikel menyatu, layar pemain di sekitar menggelap berdenyut, cincin kejut dan petir visual muncul, lalu
ledakan kembang api, totem, dan judul besar ke seluruh server (`⚔ <senjata> ⚔` / `diklaim oleh <pemain>`). Senjata baru diberikan
**setelah** scene berakhir. Karena klaim sudah tersimpan lebih dulu, server mati di tengah scene tidak menghilangkan senjata
(diberikan saat pemenang login). Semua bagian dapat diatur/dimatikan di bagian `cinematic:` pada `config.yml`.
Di Bedrock: partikel, suara, judul, kembang api, petir, dan efek gelap berfungsi; animasi senjata (ItemDisplay) tetap belum terverifikasi.

### Kamera sinematik (1.2.0)
Selama scene, pemenang dipindah ke mode Spectator dan kameranya memutari altar + dirinya sendiri (`cinematic.camera`). Tubuh pemenang
digantikan boneka armor stand (kepala/armor/item sama, tangan terangkat, melayang naik) agar terlihat di scene. Di akhir scene pemenang
dikembalikan ke lokasi dan mode semula. Pengaman: data pemulihan disimpan di data pemain, jadi bila server crash atau pemain keluar di
tengah scene, mode dan lokasinya dipulihkan saat login. Teleport spectator dicegah selama scene. Kamera bergerak dengan teleport tiap tick,
jadi bisa terlihat sedikit patah-patah pada koneksi buruk. Matikan dengan `cinematic.camera: false`.

## Konfigurasi
Semua teks hologram, pengumuman, pesan, efek (partikel/suara), tinggi senjata/hologram, radius area, dan proteksi ada di `config.yml` dan dikomentari. Placeholder hologram: `{weapon} {status} {leader} {leader_percent} {last} {claimant} {seconds} {owner}`, plus baris `{lore}` dan `{requirements}` yang diperluas ke banyak baris.

## ClearLag
Plugin ini tidak butuh konfigurasi khusus. Bila ClearLag masih sering menghapus entity altar, log akan memperingatkan; opsi terbaik adalah mengecualikan entity display di ClearLag. Item persembahan yang terhapus tetap dikembalikan.

## Geyser / Bedrock — batasan jujur
- **Hologram (TextDisplay)** diterjemahkan Geyser menjadi name tag sehingga terbaca di Bedrock.
- **Senjata (ItemDisplay)**: tampil/animasinya di Bedrock **belum terverifikasi**; efek partikel, suara, hologram, dan seluruh logika persembahan/klaim tetap berfungsi di Bedrock. Nama item di hologram memakai teks biasa agar aman di Bedrock.

## Status pengujian
Kode dikompilasi oleh CI, tetapi **belum dijalankan di server nyata**. Uji dulu di server uji: buat altar, aktifkan, persembahan oleh dua pemain bersamaan, klaim, restart saat RITUAL, dan matikan server saat altar aktif. Laporkan bug dengan log server.
