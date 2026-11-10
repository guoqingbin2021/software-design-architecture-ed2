package retail;

public final class SNBCPrinterFactory extends PrinterFactory {
    @Override
    public Printer createPrinter() {
        return new SNBCPrinter();
    }
}
