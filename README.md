# Frenky CloudStream — beta tecnica

Repository personale CloudStream con **sei provider segnaposto** e gestione centralizzata dei domini. Supporta Fire TV e tablet Android tramite CloudStream.

## Stato verificato (10 ottobre 2026)

- ✅ Repository GitHub pubblicata
- ✅ Compilazione CloudStream riuscita su GitHub Actions (workflow [build](https://github.com/Frenk0371/frenky-cloudstream/actions/workflows/build.yml))
- ✅ `MultiSource.cs3` e `plugins.json` pubblicati nel branch `builds`
- ✅ Primo controllo automatico dei sei domini eseguito con successo ([report](reports/latest.json))
- ⚠️ I sei provider sono **solo bozze**: non implementano ancora ricerca di titoli, elenco episodi o riproduzione di video
- ⚠️ Installare il pacchetto **non** significa poter vedere film o serie da questi siti

## Aggiunta del repository a CloudStream

Nel menu CloudStream vai a **Impostazioni → Estensioni → Aggiungi repository** e inserisci:

```text
https://raw.githubusercontent.com/Frenk0371/frenky-cloudstream/main/repo.json
```

Nel catalogo delle estensioni della repository cerca **MultiSource** (contrassegnata come beta). La disponibilità di estensioni beta può dipendere dalla versione di CloudStream.

Manifest: [repo.json](repo.json) · [plugins.json compilato](https://raw.githubusercontent.com/Frenk0371/frenky-cloudstream/builds/plugins.json) · [build .cs3](https://raw.githubusercontent.com/Frenk0371/frenky-cloudstream/builds/MultiSource.cs3).

## Fonti iniziali (indirizzi indicati dall'utente)

1. AltaDefinizione — altadefinizionex.surf
2. Eurostream — eurostream.pics
3. GuardaSerie — guardaseriex.click
4. StreamingCommunity — streamingcommunityz.rip
5. Il Genio dello Streaming — il-geniodellostreaming.vip
6. CB01 — cbin99.mobi/casa/

Sono solamente gli indirizzi iniziali: **non è stata verificata alcuna licenza di distribuzione** e il progetto non contiene software per estrarre video o aggirare protezioni.

## Aggiornamento centralizzato dei domini

Il file [config/domains.json](config/domains.json) mantiene gli indirizzi correnti. La GitHub Action [check-domains.yml](.github/workflows/check-domains.yml) effettua un controllo ogni giorno e, dopo i test, registra i risultati in [reports/latest.json](reports/latest.json).

- Reindirizzamento HTTPS **301/308** verso hostname con stesso nome principale → aggiorna automaticamente il registro
- Destinazione diversa, redirect temporaneo, errore di accesso o DNS → **non** aggiorna; segnala la necessità di revisione
- Scomparsa senza redirect → il sistema **non può indovinare** il nuovo dominio
- Modifica del layout di un sito → servirà un nuovo adattatore; cambiare dominio non basta

Il criterio dei redirect è prudente, ma non prova l'identità né la liceità della destinazione.

Le estensioni possono leggere la configurazione aggiornata senza reinstallazione. Attualmente questa lettura avviene durante la ricerca dei provider segnaposto, che ritorna tuttavia un risultato vuoto.

## Comandi per sviluppo e verifiche

```sh
python -m unittest discover -s tests -v
python tools/check_domains.py
python tools/check_domains.py --apply
gradle make makePluginsJson
```

Per sviluppare provider reali occorre implementare ricerca, parsing delle schede e gestione degli episodi soltanto per fonti con accesso e distribuzione autorizzati. Evitare estrazione non autorizzata, aggiramento di DRM o controlli anti-bot.

