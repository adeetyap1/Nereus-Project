# NereusAFK 1.0.0

Zona AFK: pemain yang berdiri di zona mengumpulkan **fragmen AFK**, lalu menukarnya di **villager penukar** (GUI chest, jalan di Java dan Bedrock).

## Pasang
Upload jar ke `plugins/`, restart. Butuh Paper 1.21.x. Tidak ada dependency lain.

## Setup (di dalam game, sebagai OP)
```
/afk admin zone create lobby-afk 8 6   (berdiri di tengah area AFK; radius 8, tinggi 6)
/afk admin setshop                      (villager penukar muncul di tempatmu berdiri)
```
Hapus: `/afk admin zone remove <id>`, `/afk admin removeshop` (villager terdekat dalam 5 blok).

## Perintah
- `/afk` info fragmen, `/afk shop` buka toko (bisa dimatikan di config)
- Admin (`nereusafk.admin`): `/afk admin zone create|remove|list`, `setshop`, `removeshop`, `give|take|set <pemain> <jumlah>`, `reload`

## Cara kerja
- Tiap `afk.interval-seconds` (default 60 dtk) di dalam zona = `fragments-per-interval` fragmen (default 1), maksimal `daily-cap` per hari (default 180, reset tengah malam `timezone`). Keluar zona mereset hitungan interval berjalan.
- Fragmen disimpan sebagai angka (bukan item), jadi tidak bisa diduplikasi.
- Isi toko ada di `config.yml` bagian `shop.items`: harga fragmen, batas beli harian, dan perintah konsol hadiah (`%player%`). Bawaan: NR (`eco give`), golden apple, dan kunci crate. Sesuaikan harga agar seimbang dengan ekonomi servermu.
- Data: `data.yml` (ditulis asinkron tiap 2 menit saat ada perubahan, dan saat server mati), zona: `zones.yml`.

## Catatan jujur
- Belum diuji di server nyata (dikompilasi oleh CI).
- Zona AFK tidak mendeteksi bot/auto-clicker; pagar yang dipakai adalah batas harian. Mencegah kick karena idle: `player-idle-timeout` di server.properties harus 0 atau lebih lama dari sesi AFK.
- Villager toko tidak dilindungi dari plugin pembersih entity (mis. ClearLag); kecualikan villager bernama toko bila perlu.
