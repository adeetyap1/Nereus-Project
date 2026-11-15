# NereusTotemStack

Plugin Paper untuk server **Nereus MC**: Totem of Undying bisa ditumpuk sampai 64 (nyata, bukan virtual) dan otomatis aktif dari hotbar / inventory saat pemain menerima damage fatal.

- Minecraft: Paper 1.21.11, Java 21, Maven
- Package: `id.nereusmc.totemstack`
- Versi: 1.0.0 (versi diatur di `pom.xml`; `plugin.yml` memakai `${project.version}`)
- Soft dependency: PlaceholderAPI, Geyser, Floodgate (tidak wajib; tidak ada integrasi khusus yang diperlukan)

## Build

```bash
mvn clean package
```

JAR hasil build ada di `target/NereusTotemStack-1.0.0.jar`. Salin ke folder `plugins/` lalu restart server.

## Cara kerja

**Stacking.** Setiap totem diberi komponen vanilla `max_stack_size` lewat `ItemMeta#setMaxStackSize`. Karena komponen ini dipahami server sendiri, penggabungan, split klik kanan, drag, shift-click, chest/barrel/shulker/ender chest, logout dan restart bekerja seperti item biasa. Totem diberi komponen saat: pemain join, mengambil item, item muncul (drop), loot di-generate, dan container dibuka. Tidak ada scan berkala.

**Aktivasi.** Plugin memakai `EntityResurrectEvent`. Tangan (main hand lalu off hand) ditangani vanilla. Jika tangan kosong dari totem, plugin mencari di hotbar / inventory utama sesuai urutan di config, mengambil tepat 1 totem, lalu membatalkan kematian lewat event itu. Satu kejadian fatal = satu totem; ada penjaga agar tidak terjadi double activation. Efek Regeneration, Absorption, Fire Resistance diterapkan dari config (default sama dengan vanilla).

**Statistik.** Disimpan di `plugins/NereusTotemStack/stats.db` (SQLite bawaan Paper). Penulisan dilakukan async, satu kali setelah jeda `statistics.save-interval`, dan sekali lagi saat server berhenti.

## Command

| Command | Fungsi | Permission |
|---|---|---|
| `/nereustotem` | Buka menu utama | `nereustotem.gui` |
| `/nereustotem reload` | Reload config | `nereustotem.admin` |
| `/nereustotem give <player> <amount>` | Beri totem (1–2304) | `nereustotem.admin` |
| `/nereustotem check <player>` | Jumlah totem pemain | `nereustotem.admin` |
| `/nereustotem stats` | Statistik server | `nereustotem.gui` |

Alias: `/ntotem`.

## Permission (LuckPerms)

- `nereustotem.admin` — semua fitur admin (mencakup `gui` dan `use`). Default: op.
- `nereustotem.use` — aktivasi otomatis dari inventory. Default: semua pemain.
- `nereustotem.gui` — membuka menu. Default: op.

Contoh: `lp group default permission set nereustotem.use true`

## GUI

Menu utama (45 slot, tema gray + gold): Totem Settings, Stack Settings, Activation Settings, Effect Settings, Statistics, Reload, Close.

- **Stack**: aktif/nonaktif stacking, batas 16 / 32 / 64 / 99.
- **Activation**: aktivasi inventory, hotbar, inventory utama, urutan pencarian.
- **Effects**: toggle, durasi (klik = ±5 dtk, shift = ±30 dtk), level tiap efek.
- **Statistics**: total aktivasi, total totem terpakai, top 5 pemain, statistikmu.
- **Totem Settings**: saklar utama plugin dan reset ke default (dengan konfirmasi).

Semua perubahan langsung disimpan ke `config.yml`. Hanya pemilik `nereustotem.admin` yang dapat mengubah pengaturan; semua klik di menu dibatalkan sehingga tidak ada item yang bisa diambil atau di-shift-click.

## Konfigurasi

Lihat `config.yml` (berkomentar). Batas stack valid: 2–99 (batas komponen vanilla). Nilai yang tidak valid dikembalikan otomatis saat load.

## Catatan penting

- **Menurunkan batas stack / menonaktifkan stacking**: totem yang sudah membawa komponen di-update saat pemain join/membuka container. Stack yang sudah melebihi batas baru tidak dihapus, hanya tidak bisa digabung lagi.
- **Menghapus plugin**: totem yang sudah diberi komponen tetap bisa ditumpuk (data ada di item).
- **Tangan selalu ditangani vanilla**: opsi `MAIN_HAND` / `OFF_HAND` di `priority` hanya penanda; yang diatur hanyalah urutan `HOTBAR` vs `INVENTORY`.
- **Bedrock (Geyser)**: logika server sama untuk Java dan Bedrock. Tampilan jumlah stack di klien Bedrock bergantung pada versi Geyser; uji di server Nereus sebelum diumumkan.

## Checklist pengujian (manual di server)

1. 64 totem dalam satu slot; gabung dua stack; split klik kanan; shift-click.
2. Simpan/ambil di chest, barrel, shulker box, ender chest.
3. Mati dengan 64 totem → hidup dengan 63 → 62.
4. Aktivasi dari off hand, hotbar, inventory utama; tanpa totem → mati normal.
5. Izin GUI (pemain biasa vs admin); perubahan GUI tetap setelah restart.
6. Statistik tetap setelah restart; uji dengan klien Java dan Bedrock; pastikan tidak ada duplikasi/kehilangan item.
