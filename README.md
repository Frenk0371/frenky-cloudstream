# Frenky CloudStream — repository personale (base tecnica, beta)

Progetto per gestire **6 indirizzi di siti** da un registro JSON e controllare periodicamente i reindirizzamenti. La struttura è ispirata a [TestPlugins di CloudStream](https://github.com/recloudstream/TestPlugins).

> **Stato reale:** il monitoraggio dei domini è pronto, ma i provider Kotlin sono **segnaposto**. Non cercano né riproducono video. La build Android/CloudStream deve ancora essere verificata.

## File principali

- `config/domains.json`: sei indirizzi e relative URL aggiornabili.
- `tools/check_domains.py`: controllo HTTP(S) e valutazione dei redirect.
- `reports/latest.json`: ultimo rapporto (generato dalle Actions).
- `.github/workflows/check-domains.yml`: controllo quotidiano.
- `.github/workflows/build.yml`: tentativo di build dell'estensione CloudStream.
- `MultiSource/`: sei provider segnaposto e lettura del registro remoto.

## Aggiornamento dei domini

Vengono applicati automaticamente soltanto reindirizzamenti permanenti HTTPS (301/308) con lo stesso nome principale; cambi di nome, redirect temporanei o siti irraggiungibili vengono segnalati e richiedono revisione. Questa regola non prova l'identità del sito: verificare sempre le nuove destinazioni.

## Test

```sh
python -m unittest discover -s tests -v
python tools/check_domains.py
python tools/check_domains.py --apply
```

## Installazione CloudStream

La versione beta **non è pronta per la ricerca di film o serie**. Per ogni sito occorre sviluppare e verificare un adattatore compatibile con le autorizzazioni del servizio. Le sorgenti fornite non sono state verificate come distributori autorizzati. Nessun supporto per l'aggiramento di DRM o protezioni.

I file `repo.json` e `.github/workflows/build.yml` sono predisposti, ma il link CloudStream sarà utilizzabile soltanto dopo una build riuscita e la pubblicazione di `builds/plugins.json`.
