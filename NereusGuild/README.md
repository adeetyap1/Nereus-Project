# NereusGuild

Rank guild petualang F sampai SSS untuk Nereus MC (Paper 1.21.11): GP, ujian promosi, dan permission quest otomatis.

## Alur pemain

1. Pemain mulai di rank F dan mengumpulkan GP dari quest (ODailyQuests memberi GP lewat `points give`, disimpan PlayerPoints).
2. Buka `/guild` (alias `/advrank`). Menu menunjukkan rank, GP, syarat rank berikutnya, dan tombol ujian.
3. Kalau GP cukup, klik **Mulai ujian**. Ujian bisa bunuh mob dengan batas waktu (KILL), atau diselesaikan staff atau dungeon (MANUAL).
4. Lulus ujian: rank naik, permission quest berganti, meta tampilan diperbarui, quest baru dibagikan, dan rank tinggi diumumkan ke server.
5. Gagal atau waktu habis: ada jeda sebelum boleh mengulang (`exam.cooldown-minutes`).

## Aturan quest

Rank X boleh quest X, semua rank di bawahnya, dan satu tingkat di atas X. Plugin memberi permission langsung ke pemain (tanpa group LuckPerms):

- `nereus.guild.<rank>` untuk rank pemain dan semua di bawahnya.
- `nereus.guild.tantangan.<rank>` hanya untuk satu rank di atas pemain, dan tidak menumpuk.

Permission itu dipakai `required_permissions` di file quest ODailyQuests, jadi tidak perlu diatur lagi lewat LuckPerms.
Pemain yang punya permission `*` (owner) otomatis melihat semua quest.

## Perintah

```
/guild                          menu guild
/guild info [pemain]            rank, GP, ujian (pemain lain: admin)
/guild abandon                  batalkan ujian sendiri
/guild set <pemain> <RANK>      atur rank langsung (admin)
/guild exam complete <pemain>   luluskan ujian MANUAL (admin, dungeon, atau konsol)
/guild exam cancel <pemain>     batalkan ujian pemain (admin)
/guild sync <pemain>            terapkan ulang permission dan meta (admin)
/guild reload                   muat ulang config (admin)
```

Permission: `nereusguild.use` (semua pemain), `nereusguild.admin` (op).

## Mengatur isi

Semua di `plugins/NereusGuild/config.yml`: daftar rank, GP minimal, ujian tiap rank, hadiah naik rank,
perintah yang dijalankan saat naik rank, dan semua pesan. Angka GP dan ujian bawaan hanya usulan.

Perintah bawaan yang dijalankan saat naik rank:

- `lp user %player% meta set adv_rank %rank%` mengisi tampilan TAB (`%luckperms_meta_adv_rank%`).
- `dqa reset quests %player%` membagi quest baru supaya quest rank baru langsung muncul.

Guild Master NPC (Citizens): tambahkan perintah klik `guild` yang dijalankan sebagai pemain.

## Kebutuhan

- Paper 1.21.11, Java 21 atau lebih baru.
- PlayerPoints untuk GP. Tanpa PlayerPoints, set `points.enabled: false` (syarat GP diabaikan).
- LuckPerms dan ODailyQuests (opsional, dipakai oleh perintah bawaan di atas).

Data pemain di `plugins/NereusGuild/players.yml`.
