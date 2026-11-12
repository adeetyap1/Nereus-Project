# NereusPets

Plugin pet ala Pokémon untuk Nereus MC (Paper 1.21.11). Mob vanilla versi mini, tanpa resource pack.

## Cara build (untuk developer)

Butuh JDK 21 atau lebih baru dan Maven, dengan internet untuk mengunduh Paper API.

```
mvn package
```

Hasilnya `target/NereusPets-1.0.0.jar`. Taruh di folder `plugins`, lalu restart server.

Kode ini belum pernah dikompilasi terhadap Paper API (disusun tanpa akses internet). Kalau `mvn package`
menampilkan error, kirim pesan error-nya apa adanya supaya mudah diperbaiki.

## Cara main

1. Dapatkan Pet Orb (lihat "Untuk admin"). Orb adalah bola salju bertanda.
2. Lemahkan mob liar, lalu lempar orb. Peluang tangkap makin besar kalau HP mob rendah.
3. Buka Pet Box dengan `/pets` (atau klik kanan pet), lalu klik kiri pet untuk memanggil atau menyimpannya.
4. Pet mini mengikuti pemain, menyerang apa yang diserang pemain, dan membalas yang menyerang pemain.
   Pet juga menyerang monster di sekitar secara otomatis.
5. Pet naik level dari monster yang dikalahkan, dan skill terbuka di level tertentu.
6. Kalau HP pet habis, pet pingsan selama 60 detik lalu bisa dipanggil lagi. Pet tidak pernah mati permanen.

Perintah pemain: `/pets`, `/pets recall`, `/pets rename <nama>`, `/pets info`, `/pets release <nomor>`.

## Untuk admin

```
/pets give orb <pemain> <basic|great|ultra> [jumlah]
/pets give pet <pemain> <JENIS> [level]
/pets reload
```

Permission: `nereuspets.use` (default semua pemain), `nereuspets.admin` (default op).

Hadiah quest ODailyQuests, misalnya:

```
- "pets give orb %player% basic 3"
```

## Mengatur isi

Semua ada di `plugins/NereusPets/config.yml`: ukuran pet (`pet-scale`), daftar jenis dan stat per jenis,
tier orb, peluang tangkap, XP, dan semua pesan. Menambah jenis pet = menambah blok di `species`.
Pakai mob berbasis goal AI (zombie, skeleton, laba-laba, dll). Hindari mob terbang dan mob berbasis
brain seperti piglin, hoglin, warden, dan allay.

Data pemain tersimpan di `plugins/NereusPets/data/<uuid>.yml`.

## Daftar tes sebelum dibuka ke pemain

- Lempar orb ke ayam atau zombie, pastikan bisa ditangkap dan masuk Pet Box.
- Panggil pet: ukurannya mengecil, nama `[Lv.x]` tampil, dan pet mengikuti pemain.
- Pukul zombie: pet ikut menyerang. Bunuh zombie: pet dapat XP.
- Biarkan pet kalah: pet pingsan, hilang dari dunia, dan tidak kehilangan data.
- Relog: pet terakhir terpanggil lagi, dan level serta XP tidak hilang.
- Tes dari Bedrock: orb bisa dilempar, menu bisa dibuka, dan ukuran mini tampil (bergantung versi Geyser).

## Update: tier, duel, pet liar, Legendaris

- **38 jenis mob** dengan 5 tier (Biasa, Tidak Biasa, Langka, Epik, Legendaris). Legendaris: Golem Besi, Warden, Elder Guardian, Wither, Naga Ender.
- **Gaya serang sesuai mobnya**: panah (Skeleton/Stray/Pillager), bola api (Blaze/Ghast), tengkorak Wither, ledakan sonik (Warden), napas naga area, sinar (Guardian), ramuan (Witch), trisula (Drowned), ledakan area (Creeper).
- **Pet terkontrol**: mob terbang/berbasis brain (Warden, Naga, Wither, Ghast, Blaze, dll.) digerakkan plugin, bukan AI vanilla.
- **Duel pet vs pet pemain lain**: `/pets` lalu tombol *Arena Duel*, atau `/pets duel <pemain>`, `/pets duel accept|deny`. Tidak ada pingsan, pet dipulihkan, pemenang dapat XP, rekor menang/kalah tersimpan.
- **Pet liar** tersebar dan jarang (`wild:` di config). Tidak menyerang, HP mentok 1, hilang setelah 10 menit.
- **Legendaris tiap 10 menit** (`legendary:`): satu pet muncul dekat pemain acak, diumumkan dengan koordinat kasar. Peluang tangkap sangat kecil (0.5%-30%). `/pets legend` untuk status.
- **Gacha**: `/pets give egg <pemain> <normal|rare|legend> [n]` (item, klik kanan menetas) atau `/pets give hatch <pemain> <tier>` (langsung dapat pet). Cocok untuk perintah hadiah crate.
- **Naik level**: XP dari bertarung dan duel, permen (`/pets give candy <pemain> <small|medium|rare>`), dan **fusion bintang** `/pets fuse <target#> <bahan#> confirm` (maks 5 bintang, +8% stat per bintang).
- Admin uji: `/pets wild legendary`, `/pets wild <JENIS>`.
