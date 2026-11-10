package retail;

public final class SissPrinterFactory extends PrinterFactory {
    @Override
    public Printer createPrinter() {
        return new SissPrinter();
    }
}
