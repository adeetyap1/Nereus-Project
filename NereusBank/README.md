# NereusBank 2.0.0 — Bank Nerus Guild

Bank bertema RPG isekai, **tanpa resource pack** (ikon item vanilla, judul teks biasa, sama untuk Java & Bedrock).

- Rekening, Titip/Ambil Koin, Kirim Koin, Buku Catatan, Segel Simpanan (deposito), Hutang Guild, Kartu Guild, Daftar Konglomerat
- Tier: Perunggu → Perak → Emas → Platina (config `tiers`, ikon `material`, slot `vault-slots`)
- **Brankas Item** (`/bank vault`): simpan item per pemain di `vaults/<uuid>.yml`
- **Bonus RPG Rank** F–S dari NerusRPG (`guild-perks`, via `%nereusrpg_rank_plain%`)
- Tampilan koin Platina/Emas/Perak/Tembaga (`coins`)
- NPC Teller FancyNpcs (`teller.npc-ids`)
- ATM blok, PlaceholderAPI `%nbank_*%`

## Upgrade dari 1.x
Cadangkan lalu hapus `config.yml` dan `messages.yml` lama agar default baru dibuat. `accounts.yml` tetap dipakai.
