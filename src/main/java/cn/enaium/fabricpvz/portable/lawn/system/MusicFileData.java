/*
 * Ported from src/Lawn/System/Music.h:82-87.
 */
package cn.enaium.fabricpvz.portable.lawn.system;

/**
 * Ported from src/Lawn/System/Music.h:82-87 (class {@code MusicFileData}).
 *
 * <p>Upstream this struct holds the single raw member {@code unsigned int* mFileData}: the music
 * loader {@code Music::PvzpLoadMusic} reads the whole song file into memory and keeps the buffer
 * alive only for the credits song, because {@code Mix_LoadMUS_RW} does not copy the bytes it is
 * handed (Music.cpp:82-108). Here the decoded stream is owned by the concrete
 * {@link cn.enaium.fabricpvz.portable.sexy.sound.MusicInterface} implementation, so this field is retained for
 * source parity and left {@code null}; nothing consumes it.
 */
public class MusicFileData {

    /**
     * Upstream {@code unsigned int* mFileData} (Music.h:85): the raw file buffer whose lifetime the
     * C++ code had to extend past the load call. Java keeps no raw pointer; the concrete
     * MusicInterface owns the decoded stream instead.
     */
    public byte[] mFileData;
}
