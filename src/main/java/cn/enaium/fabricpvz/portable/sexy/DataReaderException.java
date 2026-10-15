package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/Lawn/System/DataSync.h:48-50.
 *
 * <p>Thrown by {@link DataReader} when a read runs past the end of the stream, or when reading from
 * a file fails. The upstream type derives from {@code std::exception} and carries no payload; in
 * Java it is an unchecked {@link RuntimeException} so save-file code does not have to be littered
 * with {@code throws} clauses.
 */
public class DataReaderException extends RuntimeException {

    public DataReaderException() {
        super();
    }

    public DataReaderException(String theMessage) {
        super(theMessage);
    }

    public DataReaderException(String theMessage, Throwable theCause) {
        super(theMessage, theCause);
    }
}
