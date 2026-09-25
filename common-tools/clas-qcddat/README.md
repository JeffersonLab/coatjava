# QCDDAT Tools

> [!NOTE]
> See [documentation from Veronique](https://clasweb.jlab.org/wiki/images/d/d0/CVT_QCDDAT_Subpackage_Documentation.pdf), the original
> developer; see also [Veronique's wikipage for further documentation](https://clasweb.jlab.org/wiki/index.php/Veronique_Ziegler_Documentation).

## `CVT::QCDDATHit` Bank Validation

Run reconstruction, _e.g._,
```bash
run-clara -y $COATJAVA/etc/services/mc-qcddat.yaml -t 4 -n 500 -c ./clara -o ./clarout raw.evio
```

Run the CVT event display:
```bash
run-coatjava org.jlab.qcddat.CVTBrowser clarout/rec_raw.evio.hipo
```
