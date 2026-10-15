/*
 * Ported from src/Lawn/System/Music.h:89-137 and src/Lawn/System/Music.cpp.
 */
package cn.enaium.fabricpvz.portable.lawn.system;

import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.MusicBurstState;
import cn.enaium.fabricpvz.portable.lawn.MusicDrumsState;
import cn.enaium.fabricpvz.portable.lawn.MusicFile;
import cn.enaium.fabricpvz.portable.lawn.MusicTune;
import cn.enaium.fabricpvz.portable.lawn.PvzpCurves;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpDebug;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.sound.MusicInterface;

/**
 * Ported from src/Lawn/System/Music.h:89-137 (class {@code Music}) and
 * src/Lawn/System/Music.cpp (implementation).
 *
 * <p>Owns the game's streamed music: the MOD "main music" stream plus the drums stream layered on
 * top of it, the burst/drums state machine, the fade-out counter and the pause/resume bookkeeping.
 * The concrete decoder backend is the process-wide
 * {@link cn.enaium.fabricpvz.portable.sexy.sound.MusicInterface} (upstream {@code SDLMusicInterface}); every
 * {@code SDL_mixer-ext} call in the C++ is replaced by a {@link MusicInterface} call here.
 *
 * <p><b>Omitted member:</b> upstream {@code Music::GetMusicHandle(MusicFile)} (Music.cpp:230-237)
 * has no Java analogue — song ids ARE the handles here, so the method is not ported. Call sites
 * that used the handle (the channel-volume setup, the scheme-2 drum stream order jump) call the
 * {@link MusicInterface} directly instead.
 */
public class Music {

    /** Upstream global {@code MusicFileData gMusicFileData[MusicFile::NUM_MUSIC_FILES]} (Music.h:87, Music.cpp:59). */
    public static MusicFileData[] gMusicFileData = new MusicFileData[MusicFile.NUM_MUSIC_FILES];

    static {
        for (int i = 0; i < gMusicFileData.length; i++) {
            gMusicFileData[i] = new MusicFileData();
        }
    }

    /**
     * Sentinel returned by {@link #selectGameTune} for the stormy-night branch (Music.cpp:608)
     * where upstream calls {@code StopAllMusic()} rather than playing a tune. Java-only; the C++
     * has no such constant.
     */
    public static final int MUSIC_TUNE_STOP = -2;

    // ------------------------------------------------------------------------------------------
    // Member state (Music.h:90-111; init in Music.cpp:35-57)
    // ------------------------------------------------------------------------------------------

    /** Ported from src/Lawn/System/Music.h:92. */
    public LawnApp mApp;

    /** Ported from src/Lawn/System/Music.h:93. */
    public MusicInterface mMusicInterface;

    /** Ported from src/Lawn/System/Music.h:94. */
    public int mCurMusicTune;

    /** Ported from src/Lawn/System/Music.h:95. */
    public int mCurMusicFileMain;

    /** Ported from src/Lawn/System/Music.h:96. */
    public int mCurMusicFileDrums;

    /** Ported from src/Lawn/System/Music.h:97. */
    public int mCurMusicFileHihats;

    /** Ported from src/Lawn/System/Music.h:98. */
    public int mBurstOverride;

    /** Ported from src/Lawn/System/Music.h:99. */
    public float mBaseBPM;

    /** Ported from src/Lawn/System/Music.h:100. */
    public float mBaseModSpeed;

    /** Ported from src/Lawn/System/Music.h:101. */
    public int mMusicBurstState;

    /** Ported from src/Lawn/System/Music.h:102. */
    public int mBurstStateCounter;

    /** Ported from src/Lawn/System/Music.h:103. */
    public int mMusicDrumsState;

    /** Ported from src/Lawn/System/Music.h:104. */
    public int mQueuedDrumTrackPackedOrder;

    /** Ported from src/Lawn/System/Music.h:105. */
    public int mDrumsStateCounter;

    /** Ported from src/Lawn/System/Music.h:106. */
    public int mPauseOffset;

    /** Ported from src/Lawn/System/Music.h:107. */
    public int mPauseOffsetDrums;

    /** Ported from src/Lawn/System/Music.h:108. */
    public boolean mPaused;

    /** Ported from src/Lawn/System/Music.h:109. */
    public boolean mMusicDisabled;

    /** Ported from src/Lawn/System/Music.h:110. */
    public int mFadeOutCounter;

    /** Ported from src/Lawn/System/Music.h:111. */
    public int mFadeOutDuration;

    // ------------------------------------------------------------------------------------------
    // Loading tables (Music.cpp:65-73)
    // ------------------------------------------------------------------------------------------

    /** Upstream {@code MUSIC_LOADING_TASK_WEIGHT} (Music.cpp:67). */
    private static final int MUSIC_LOADING_TASK_WEIGHT = 3500;

    /** Ported from src/Lawn/System/Music.cpp:68-71 (struct MusicLoadEntry). */
    private record MusicLoadEntry(int mMusicFile, String mFileName) {
    }

    /** Ported from src/Lawn/System/Music.cpp:68-71 ({@code MUSIC_LOADING_FILES}). */
    private static final MusicLoadEntry[] MUSIC_LOADING_FILES = {
        new MusicLoadEntry(MusicFile.MUSIC_FILE_DRUMS, "sounds/mainmusic.mo3"),
        new MusicLoadEntry(MusicFile.MUSIC_FILE_CREDITS_ZOMBIES_ON_YOUR_LAWN, "sounds/ZombiesOnYourLawn.ogg")
    };

    /**
     * Ported from src/Lawn/System/Music.cpp:73 ({@code static const int Music::MUSIC_LOADING_TASKS});
     * {@code 3500 * MUSIC_LOADING_FILES.length = 7000}.
     */
    public static final int MUSIC_LOADING_TASKS = MUSIC_LOADING_TASK_WEIGHT * MUSIC_LOADING_FILES.length;

    // ------------------------------------------------------------------------------------------
    // Construction (Music.cpp:35-57)
    // ------------------------------------------------------------------------------------------

    /** Ported from src/Lawn/System/Music.cpp:35-57. */
    public Music() {
        mApp = (SexyAppBase.current instanceof LawnApp aLawnApp) ? aLawnApp : null;
        mMusicInterface = (SexyAppBase.current != null) ? SexyAppBase.current.mMusicInterface : null;
        mCurMusicTune = MusicTune.MUSIC_TUNE_NONE;
        mCurMusicFileMain = MusicFile.MUSIC_FILE_NONE;
        mCurMusicFileDrums = MusicFile.MUSIC_FILE_NONE;
        mCurMusicFileHihats = MusicFile.MUSIC_FILE_NONE;
        mBurstOverride = -1;
        mMusicDrumsState = MusicDrumsState.MUSIC_DRUMS_OFF;
        mDrumsStateCounter = 0;
        mQueuedDrumTrackPackedOrder = -1;
        mBaseBPM = 155.0f;
        mBaseModSpeed = 3.0f;
        mMusicBurstState = MusicBurstState.MUSIC_BURST_OFF;
        mBurstStateCounter = 0;
        mPauseOffset = 0;
        mPauseOffsetDrums = 0;
        mPaused = false;
        mMusicDisabled = false;
        mFadeOutCounter = 0;
        mFadeOutDuration = 0;
    }

    // ------------------------------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------------------------------

    /**
     * Ported from src/Lawn/System/Music.cpp:75-116 ({@code Music::PvzpLoadMusic}).
     *
     * <p>Upstream opens the (pak-aware) file, decodes it through {@code Mix_LoadMUS_RW} and inserts
     * an {@code SDLMusicInfo} into {@code SDLMusicInterface::mMusicMap}. Here the concrete
     * {@link MusicInterface} owns the decoded stream, so this simply registers {@code theMusicFile}
     * under {@code theFileName}; the raw-buffer lifetime dance (Music.cpp:98-101, kept only for the
     * credits song in {@code gMusicFileData[file].mFileData}) does not apply — see
     * {@link MusicFileData}.
     */
    public boolean pvzpLoadMusic(int theMusicFile, String theFileName) {
        if (mMusicInterface == null) {
            return false;
        }
        return mMusicInterface.loadMusic(theMusicFile, theFileName);
    }

    /**
     * Ported from src/Lawn/System/Music.cpp:175-182 ({@code Music::LoadSong}): load, or log
     * {@code "music failed to load"} and disable music.
     */
    public void loadSong(int theMusicFile, String theFileName) {
        if (!pvzpLoadMusic(theMusicFile, theFileName)) {
            // PORT-NOTE: WARN, not the INFO upstream used: the failure disables music and the only
            // other line about it (OpenMptLibrary's "Music disabled") never comes from here.
            Common.logWarnLn("music failed to load");
            mMusicDisabled = true;
        }
    }

    /**
     * Ported from src/Lawn/System/Music.cpp:190-197 ({@code Music::MusicInit}): load the two
     * {@code MUSIC_LOADING_FILES} entries and credit 3500 completed loading tasks each into the
     * app's loading bar.
     */
    public void musicInit() {
        for (MusicLoadEntry aMusic : MUSIC_LOADING_FILES) {
            loadSong(aMusic.mMusicFile(), aMusic.mFileName());
            if (mApp != null) {
                mApp.mCompletedLoadingThreadTasks.addAndGet(MUSIC_LOADING_TASK_WEIGHT);
            }
        }
    }

    /**
     * Ported from src/Lawn/System/Music.h:119 ({@code void MusicDispose() { ; }}): upstream no-op.
     * Music handles live in the concrete MusicInterface.
     */
    public void musicDispose() {
    }

    /**
     * Ported from src/Lawn/System/Music.cpp:184-188 ({@code Music::MusicTitleScreenInit}).
     */
    public void musicTitleScreenInit() {
        loadSong(MusicFile.MUSIC_FILE_MAIN_MUSIC, "sounds/mainmusic.mo3");
        makeSureMusicIsPlaying(MusicTune.MUSIC_TUNE_TITLE_CRAZY_DAVE_MAIN_THEME);
    }

    /**
     * Ported from src/Lawn/System/Music.cpp:199-205 ({@code Music::MusicCreditScreenInit}).
     *
     * <p><b>Dead code upstream</b> — there is no caller anywhere in {@code src/} — but kept for
     * source parity. The original guards on the credits song being absent from
     * {@code SDLMusicInterface::mMusicMap}; that map is owned by the concrete MusicInterface here,
     * so the presence check has no analogue and the load is issued unconditionally.
     */
    public void musicCreditScreenInit() {
        loadSong(MusicFile.MUSIC_FILE_CREDITS_ZOMBIES_ON_YOUR_LAWN, "sounds/ZombiesOnYourLawn.ogg");
    }

    // ------------------------------------------------------------------------------------------
    // Playback control
    // ------------------------------------------------------------------------------------------

    /**
     * Ported from src/Lawn/System/Music.cpp:207-227 ({@code Music::StopAllMusic}): stop both live
     * streams and reset all playback/burst/drums state.
     */
    public void stopAllMusic() {
        if (mMusicInterface != null) {
            if (mCurMusicFileMain != MusicFile.MUSIC_FILE_NONE) {
                mMusicInterface.stopMusic(mCurMusicFileMain);
            }
            if (mCurMusicFileDrums != MusicFile.MUSIC_FILE_NONE) {
                mMusicInterface.stopMusic(mCurMusicFileDrums);
            }
        }

        mCurMusicTune = MusicTune.MUSIC_TUNE_NONE;
        mCurMusicFileMain = MusicFile.MUSIC_FILE_NONE;
        mCurMusicFileDrums = MusicFile.MUSIC_FILE_NONE;
        mCurMusicFileHihats = MusicFile.MUSIC_FILE_NONE;
        mQueuedDrumTrackPackedOrder = -1;
        mMusicDrumsState = MusicDrumsState.MUSIC_DRUMS_OFF;
        mMusicBurstState = MusicBurstState.MUSIC_BURST_OFF;
        mPauseOffset = 0;
        mPauseOffsetDrums = 0;
        mPaused = false;
        mFadeOutCounter = 0;
    }

    /**
     * Ported from src/Lawn/System/Music.cpp:239-263 ({@code Music::PlayFromOffset}).
     *
     * <p>Upstream branches: for the credits tune it calls {@code MusicInterface::PlayMusic} with
     * {@code noLoop = (file == MUSIC_FILE_CREDITS_ZOMBIES_ON_YOUR_LAWN)}; otherwise it halts the
     * stream, sets {@code mVolume = mVolumeCap * theVolume}, restarts it with
     * {@code Mix_PlayMusicStream(handle, -1)}, jumps to {@code theOffset}, applies the volume and
     * runs {@link #setupVolumeForTune}. Java delegates the stream juggling to
     * {@link MusicInterface#playMusic} / {@link MusicInterface#setSongVolume} and only needs the
     * per-tune channel setup itself.
     */
    public void playFromOffset(int theMusicFile, int theOffset, double theVolume) {
        boolean aNoLoop = theMusicFile == MusicFile.MUSIC_FILE_CREDITS_ZOMBIES_ON_YOUR_LAWN;
        mMusicInterface.playMusic(theMusicFile, theOffset, aNoLoop);
        mMusicInterface.setSongVolume(theMusicFile, theVolume);
        setupVolumeForTune(mCurMusicTune, 0.0f, 0.0f);
    }

    /**
     * Ported from src/Lawn/System/Music.cpp:265-376 ({@code Music::PlayMusic}): the MOD order table
     * (see the class spec 1.5) that maps a tune to its main/drums file and default starting order
     * ({@code theOffset == -1} means "use the tune default").
     */
    public void playMusic(int theMusicTune, int theOffset, int theDrumsOffset) {
        if (mMusicDisabled) {
            return;
        }

        mCurMusicTune = theMusicTune;
        mCurMusicFileMain = MusicFile.MUSIC_FILE_NONE;
        mCurMusicFileDrums = MusicFile.MUSIC_FILE_NONE;
        mCurMusicFileHihats = MusicFile.MUSIC_FILE_NONE;

        switch (theMusicTune) {
            case MusicTune.MUSIC_TUNE_DAY_GRASSWALK:
                mCurMusicFileMain = MusicFile.MUSIC_FILE_MAIN_MUSIC;
                if (theOffset == -1) {
                    theOffset = 0x00;
                }
                playFromOffset(MusicFile.MUSIC_FILE_MAIN_MUSIC, theOffset, 1.0);
                break;

            case MusicTune.MUSIC_TUNE_NIGHT_MOONGRAINS:
                mCurMusicFileMain = MusicFile.MUSIC_FILE_MAIN_MUSIC;
                mCurMusicFileDrums = MusicFile.MUSIC_FILE_DRUMS;
                if (theOffset == -1) {
                    theOffset = 0x30;
                    theDrumsOffset = 0x5C;
                }
                playFromOffset(MusicFile.MUSIC_FILE_MAIN_MUSIC, theOffset, 1.0);
                playFromOffset(MusicFile.MUSIC_FILE_DRUMS, theDrumsOffset, 0.0);
                break;

            case MusicTune.MUSIC_TUNE_POOL_WATERYGRAVES:
                mCurMusicFileMain = MusicFile.MUSIC_FILE_MAIN_MUSIC;
                if (theOffset == -1) {
                    theOffset = 0x5E;
                }
                playFromOffset(MusicFile.MUSIC_FILE_MAIN_MUSIC, theOffset, 1.0);
                break;

            case MusicTune.MUSIC_TUNE_FOG_RIGORMORMIST:
                mCurMusicFileMain = MusicFile.MUSIC_FILE_MAIN_MUSIC;
                if (theOffset == -1) {
                    theOffset = 0x7D;
                }
                playFromOffset(MusicFile.MUSIC_FILE_MAIN_MUSIC, theOffset, 1.0);
                break;

            case MusicTune.MUSIC_TUNE_ROOF_GRAZETHEROOF:
                mCurMusicFileMain = MusicFile.MUSIC_FILE_MAIN_MUSIC;
                if (theOffset == -1) {
                    theOffset = 0xB8;
                }
                playFromOffset(MusicFile.MUSIC_FILE_MAIN_MUSIC, theOffset, 1.0);
                break;

            case MusicTune.MUSIC_TUNE_CHOOSE_YOUR_SEEDS:
                mCurMusicFileMain = MusicFile.MUSIC_FILE_MAIN_MUSIC;
                if (theOffset == -1) {
                    theOffset = 0x7A;
                }
                playFromOffset(MusicFile.MUSIC_FILE_MAIN_MUSIC, theOffset, 1.0);
                break;

            case MusicTune.MUSIC_TUNE_TITLE_CRAZY_DAVE_MAIN_THEME:
                mCurMusicFileMain = MusicFile.MUSIC_FILE_MAIN_MUSIC;
                if (theOffset == -1) {
                    theOffset = 0x98;
                }
                playFromOffset(MusicFile.MUSIC_FILE_MAIN_MUSIC, theOffset, 1.0);
                break;

            case MusicTune.MUSIC_TUNE_ZEN_GARDEN:
                mCurMusicFileMain = MusicFile.MUSIC_FILE_MAIN_MUSIC;
                if (theOffset == -1) {
                    theOffset = 0xDD;
                }
                playFromOffset(MusicFile.MUSIC_FILE_MAIN_MUSIC, theOffset, 1.0);
                break;

            case MusicTune.MUSIC_TUNE_PUZZLE_CEREBRAWL:
                mCurMusicFileMain = MusicFile.MUSIC_FILE_MAIN_MUSIC;
                if (theOffset == -1) {
                    theOffset = 0xB1;
                }
                playFromOffset(MusicFile.MUSIC_FILE_MAIN_MUSIC, theOffset, 1.0);
                break;

            case MusicTune.MUSIC_TUNE_MINIGAME_LOONBOON:
                mCurMusicFileMain = MusicFile.MUSIC_FILE_MAIN_MUSIC;
                if (theOffset == -1) {
                    theOffset = 0xA6;
                }
                playFromOffset(MusicFile.MUSIC_FILE_MAIN_MUSIC, theOffset, 1.0);
                break;

            case MusicTune.MUSIC_TUNE_CONVEYER:
                mCurMusicFileMain = MusicFile.MUSIC_FILE_MAIN_MUSIC;
                if (theOffset == -1) {
                    theOffset = 0xD4;
                }
                playFromOffset(MusicFile.MUSIC_FILE_MAIN_MUSIC, theOffset, 1.0);
                break;

            case MusicTune.MUSIC_TUNE_FINAL_BOSS_BRAINIAC_MANIAC:
                mCurMusicFileMain = MusicFile.MUSIC_FILE_MAIN_MUSIC;
                if (theOffset == -1) {
                    theOffset = 0x9E;
                }
                playFromOffset(MusicFile.MUSIC_FILE_MAIN_MUSIC, theOffset, 1.0);
                break;

            case MusicTune.MUSIC_TUNE_CREDITS_ZOMBIES_ON_YOUR_LAWN:
                mCurMusicFileMain = MusicFile.MUSIC_FILE_CREDITS_ZOMBIES_ON_YOUR_LAWN;
                if (theOffset == -1) {
                    theOffset = 0;
                }
                playFromOffset(MusicFile.MUSIC_FILE_CREDITS_ZOMBIES_ON_YOUR_LAWN, theOffset, 1.0);
                break;

            default:
                PvzpDebug.pvzpAssertFailed("false", "src/Lawn/System/Music.cpp", 374, "");
                break;
        }
    }

    /**
     * Ported from src/Lawn/System/Music.cpp:582-589 ({@code Music::MakeSureMusicIsPlaying}): the
     * only idempotent "play" entry point.
     */
    public void makeSureMusicIsPlaying(int theMusicTune) {
        if (mCurMusicTune != theMusicTune) {
            stopAllMusic();
            playMusic(theMusicTune, -1, -1);
        }
    }

    /**
     * Ported from src/Lawn/System/Music.cpp:379-383 ({@code Music::GetMusicOrder}).
     *
     * <p>The concrete backend returns the packed {@code (row << 16) | order} value (or {@code -1}
     * when the song is unknown / not a tracker stream); the burst state machine masks it with
     * {@code 0xFFFF} before comparing main vs. drum order (Music.cpp:506-507).
     */
    public int getMusicOrder(int theMusicFile) {
        if (theMusicFile == MusicFile.MUSIC_FILE_NONE) {
            PvzpDebug.pvzpAssertFailed("theMusicFile != MusicFile::MUSIC_FILE_NONE",
                    "src/Lawn/System/Music.cpp", 381, "");
        }
        return mMusicInterface.getMusicOrder(theMusicFile);
    }

    /**
     * Ported from src/Lawn/System/Music.cpp:118-173 ({@code Music::SetupVolumeForTune}).
     *
     * <p>The main music MOD has {@code TRACK_COUNT = 30} channels. Channels {@code 0..aMainEnd}
     * play at full volume; above that, drums channels get {@code theDrumsVolume}, hi-hats channels
     * get {@code theHihatsVolume}, a channel in both ranges gets {@code max(...)} and anything else
     * is muted. Per-tune ranges (Music.cpp:120-146): DAY main 0..23 drums 24-26 hihats 27; POOL main
     * 0..17 drums 18-28 hihats 18-24 and 29; FOG main 0..15 drums 16-22 hihats 23; ROOF main 0..17
     * drums 18-20 hihats 21; default main 0..29 with no drums/hihats. The result is pushed to the
     * backend as {@code (int)(volume * 128)} (Music.cpp:171).
     */
    public void setupVolumeForTune(int theMusicTune, float theDrumsVolume, float theHihatsVolume) {
        final int TRACK_COUNT = 30;
        int aMainEnd = 29;
        int aDrumsStart = -1, aDrumsEnd = -1;
        int aHihatsStart1 = -1, aHihatsEnd1 = -1, aHihatsStart2 = -1, aHihatsEnd2 = -1;

        switch (theMusicTune) {
            case MusicTune.MUSIC_TUNE_DAY_GRASSWALK:
                aMainEnd = 23;
                aDrumsStart = 24; aDrumsEnd = 26;
                aHihatsStart1 = 27; aHihatsEnd1 = 27;
                break;
            case MusicTune.MUSIC_TUNE_POOL_WATERYGRAVES:
                aMainEnd = 17;
                aDrumsStart = 18; aDrumsEnd = 28;
                aHihatsStart1 = 18; aHihatsEnd1 = 24; aHihatsStart2 = 29; aHihatsEnd2 = 29;
                break;
            case MusicTune.MUSIC_TUNE_FOG_RIGORMORMIST:
                aMainEnd = 15;
                aDrumsStart = 16; aDrumsEnd = 22;
                aHihatsStart1 = 23; aHihatsEnd1 = 23;
                break;
            case MusicTune.MUSIC_TUNE_ROOF_GRAZETHEROOF:
                aMainEnd = 17;
                aDrumsStart = 18; aDrumsEnd = 20;
                aHihatsStart1 = 21; aHihatsEnd1 = 21;
                break;
            default:
                break;
        }

        for (int aTrack = 0; aTrack < TRACK_COUNT; aTrack++) {
            float aVolume;
            if (aTrack <= aMainEnd) {
                aVolume = 1.0f;
            } else {
                boolean isDrums = (aTrack >= aDrumsStart && aTrack <= aDrumsEnd);
                boolean isHihats = (aTrack >= aHihatsStart1 && aTrack <= aHihatsEnd1)
                        || (aTrack >= aHihatsStart2 && aTrack <= aHihatsEnd2);
                if (isDrums && isHihats) {
                    aVolume = Math.max(theDrumsVolume, theHihatsVolume);
                } else if (isDrums) {
                    aVolume = theDrumsVolume;
                } else if (isHihats) {
                    aVolume = theHihatsVolume;
                } else {
                    aVolume = 0.0f;
                }
            }
            mMusicInterface.setChannelVolume(MusicFile.MUSIC_FILE_MAIN_MUSIC, aTrack, (int) (aVolume * 128));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Fade + burst state machine
    // ------------------------------------------------------------------------------------------

    /**
     * Ported from src/Lawn/System/Music.cpp:394-401 ({@code Music::FadeOut}): ignored unless a tune
     * is playing. {@code theFadeOutDuration} is in centiseconds and is ticked down in
     * {@link #musicUpdate}.
     */
    public void fadeOut(int theFadeOutDuration) {
        if (mCurMusicTune != MusicTune.MUSIC_TUNE_NONE) {
            mFadeOutCounter = theFadeOutDuration;
            mFadeOutDuration = theFadeOutDuration;
        }
    }

    /** Ported from src/Lawn/System/Music.cpp:385-392 ({@code Music::StartBurst}). */
    public void startBurst() {
        if (mMusicBurstState == MusicBurstState.MUSIC_BURST_OFF) {
            mMusicBurstState = MusicBurstState.MUSIC_BURST_STARTING;
            mBurstStateCounter = 400;
        }
    }

    /**
     * Ported from src/Lawn/System/Music.cpp:562-580 ({@code Music::MusicUpdate}): tick the fade
     * first, then the burst, the latter only while the board is not paused. The board-null guard
     * also covers headless tests where no app exists.
     */
    public void musicUpdate() {
        if (mFadeOutCounter > 0) {
            mFadeOutCounter--;
            if (mFadeOutCounter == 0) {
                stopAllMusic();
            } else {
                // Music.cpp:576: linear 1.0 -> 0.0 over mFadeOutDuration.
                float aFadeLevel = PvzpCommon.pvzpAnimateCurveFloat(
                        mFadeOutDuration, 0, mFadeOutCounter, 1.0f, 0.0f, PvzpCurves.CURVE_LINEAR);
                mMusicInterface.setSongVolume(mCurMusicFileMain, aFadeLevel);
            }
        }

        if (mApp == null || mApp.mBoard == null || !mApp.mBoard.mPaused) {
            updateMusicBurst();
        }
    }

    /**
     * Testability seam for {@code mApp->mBoard->CountZombiesOnScreen()} (used at Music.cpp:423 and
     * Music.cpp:491); returns 0 when there is no board so the burst machine can be driven headless.
     */
    protected int countZombiesOnScreen() {
        return (mApp != null && mApp.mBoard != null) ? mApp.mBoard.countZombiesOnScreen() : 0;
    }

    /**
     * Ported from src/Lawn/System/Music.cpp:403-560 ({@code Music::UpdateMusicBurst}): the
     * burst/drums layering state machine.
     *
     * <p>Scheme 1 covers DAY_GRASSWALK / POOL_WATERYGRAVES / FOG_RIGORMORMIST / ROOF_GRAZETHEROOF;
     * scheme 2 covers NIGHT_MOONGRAINS; any other tune returns immediately. Scheme 1 drives the
     * burst through the hi-hat/drum channel volumes via {@link #setupVolumeForTune}, scheme 2 fades
     * the main stream and mixes the drums stream directly. Counters are centiseconds (400 =
     * {@code PvzpAnimateCurveFloat(400, 0, counter, ...)}, ON runs 800 frames, scheme-2 drum fade is
     * 800, scheme-1 drum fade is 50).
     *
     * <p>The upstream board/game-mode guards run only when an app is present, so a headless test can
     * drive the machine with {@link #countZombiesOnScreen} overridden.
     */
    public void updateMusicBurst() {
        if (mApp != null && mApp.mGameMode == GameMode.GAMEMODE_INTRO) {
            return;
        }
        if (mApp != null && mApp.mBoard == null) {
            return;
        }

        int aBurstScheme;
        if (mCurMusicTune == MusicTune.MUSIC_TUNE_DAY_GRASSWALK || mCurMusicTune == MusicTune.MUSIC_TUNE_POOL_WATERYGRAVES
                || mCurMusicTune == MusicTune.MUSIC_TUNE_FOG_RIGORMORMIST || mCurMusicTune == MusicTune.MUSIC_TUNE_ROOF_GRAZETHEROOF) {
            aBurstScheme = 1;
        } else if (mCurMusicTune == MusicTune.MUSIC_TUNE_NIGHT_MOONGRAINS) {
            aBurstScheme = 2;
        } else {
            return;
        }

        int aPackedOrderMain = getMusicOrder(mCurMusicFileMain);
        if (mBurstStateCounter > 0) {
            mBurstStateCounter--;
        }
        if (mDrumsStateCounter > 0) {
            mDrumsStateCounter--;
        }

        float aFadeTrackVolume = 0.0f;
        float aDrumsVolume = 0.0f;
        float aMainTrackVolume = 1.0f;
        switch (mMusicBurstState) {
            case MusicBurstState.MUSIC_BURST_OFF:
                if (countZombiesOnScreen() >= 10 || mBurstOverride == 1) {
                    startBurst();
                }
                break;
            case MusicBurstState.MUSIC_BURST_STARTING:
                if (aBurstScheme == 1) {
                    // Music.cpp:434: linear 0.0 -> 1.0 over 400.
                    aFadeTrackVolume = PvzpCommon.pvzpAnimateCurveFloat(
                            400, 0, mBurstStateCounter, 0.0f, 1.0f, PvzpCurves.CURVE_LINEAR);
                    if (mBurstStateCounter == 100) {
                        mMusicDrumsState = MusicDrumsState.MUSIC_DRUMS_ON_QUEUED;
                        mQueuedDrumTrackPackedOrder = aPackedOrderMain;
                    } else if (mBurstStateCounter == 0) {
                        mMusicBurstState = MusicBurstState.MUSIC_BURST_ON;
                        mBurstStateCounter = 800;
                    }
                } else if (aBurstScheme == 2) {
                    if (mMusicDrumsState == MusicDrumsState.MUSIC_DRUMS_OFF) {
                        mMusicDrumsState = MusicDrumsState.MUSIC_DRUMS_ON_QUEUED;
                        mQueuedDrumTrackPackedOrder = aPackedOrderMain;
                        mBurstStateCounter = 400;
                    } else if (mMusicDrumsState == MusicDrumsState.MUSIC_DRUMS_ON_QUEUED) {
                        mBurstStateCounter = 400;
                    } else {
                        // Music.cpp:458: linear 1.0 -> 0.0 over 400.
                        aMainTrackVolume = PvzpCommon.pvzpAnimateCurveFloat(
                                400, 0, mBurstStateCounter, 1.0f, 0.0f, PvzpCurves.CURVE_LINEAR);
                        if (mBurstStateCounter == 0) {
                            mMusicBurstState = MusicBurstState.MUSIC_BURST_ON;
                            mBurstStateCounter = 800;
                        }
                    }
                }
                break;
            case MusicBurstState.MUSIC_BURST_ON:
                aFadeTrackVolume = 1.0f;
                if (aBurstScheme == 2) {
                    aMainTrackVolume = 0.0f;
                }
                if (mBurstStateCounter == 0
                        && ((countZombiesOnScreen() < 4 && mBurstOverride == -1) || mBurstOverride == 2)) {
                    if (aBurstScheme == 1) {
                        mMusicBurstState = MusicBurstState.MUSIC_BURST_FINISHING;
                        mBurstStateCounter = 800;
                        mMusicDrumsState = MusicDrumsState.MUSIC_DRUMS_OFF_QUEUED;
                        mQueuedDrumTrackPackedOrder = aPackedOrderMain;
                    } else if (aBurstScheme == 2) {
                        mMusicBurstState = MusicBurstState.MUSIC_BURST_FINISHING;
                        mBurstStateCounter = 1100;
                        mMusicDrumsState = MusicDrumsState.MUSIC_DRUMS_FADING;
                        mDrumsStateCounter = 800;
                    }
                }
                break;
            case MusicBurstState.MUSIC_BURST_FINISHING:
                if (aBurstScheme == 1) {
                    // Music.cpp:513: linear 1.0 -> 0.0 over 800.
                    aFadeTrackVolume = PvzpCommon.pvzpAnimateCurveFloat(
                            800, 0, mBurstStateCounter, 1.0f, 0.0f, PvzpCurves.CURVE_LINEAR);
                } else {
                    // Music.cpp:515: linear 0.0 -> 1.0 over 400.
                    aMainTrackVolume = PvzpCommon.pvzpAnimateCurveFloat(
                            400, 0, mBurstStateCounter, 0.0f, 1.0f, PvzpCurves.CURVE_LINEAR);
                }
                if (mBurstStateCounter == 0 && mMusicDrumsState == MusicDrumsState.MUSIC_DRUMS_OFF) {
                    mMusicBurstState = MusicBurstState.MUSIC_BURST_OFF;
                }
                break;
            default:
                break;
        }

        int aDrumsJumpOrder = -1;
        int aOrderMain = 0, aOrderDrum = 0;
        if (aBurstScheme == 1) {
            aOrderMain = aPackedOrderMain & 0xFFFF;
            aOrderDrum = mQueuedDrumTrackPackedOrder & 0xFFFF;
        } else if (aBurstScheme == 2) {
            aOrderMain = aPackedOrderMain & 0xFFFF;
            aOrderDrum = mQueuedDrumTrackPackedOrder & 0xFFFF;
        }

        switch (mMusicDrumsState) {
            case MusicDrumsState.MUSIC_DRUMS_ON_QUEUED:
                if (aOrderMain != aOrderDrum) {
                    aDrumsVolume = 1.0f;
                    mMusicDrumsState = MusicDrumsState.MUSIC_DRUMS_ON;
                    if (aBurstScheme == 2) {
                        aDrumsJumpOrder = (aOrderMain % 2 == 0) ? 76 : 77;
                    }
                }
                break;
            case MusicDrumsState.MUSIC_DRUMS_ON:
                aDrumsVolume = 1.0f;
                break;
            case MusicDrumsState.MUSIC_DRUMS_OFF_QUEUED:
                aDrumsVolume = 1.0f;
                if (aOrderMain != aOrderDrum && aBurstScheme == 1) {
                    mMusicDrumsState = MusicDrumsState.MUSIC_DRUMS_FADING;
                    mDrumsStateCounter = 50;
                }
                break;
            case MusicDrumsState.MUSIC_DRUMS_FADING:
                if (aBurstScheme == 2) {
                    // Music.cpp:543: linear 1.0 -> 0.0 over 800.
                    aDrumsVolume = PvzpCommon.pvzpAnimateCurveFloat(
                            800, 0, mDrumsStateCounter, 1.0f, 0.0f, PvzpCurves.CURVE_LINEAR);
                } else {
                    // Music.cpp:545: linear 1.0 -> 0.0 over 50.
                    aDrumsVolume = PvzpCommon.pvzpAnimateCurveFloat(
                            50, 0, mDrumsStateCounter, 1.0f, 0.0f, PvzpCurves.CURVE_LINEAR);
                }
                if (mDrumsStateCounter == 0) {
                    mMusicDrumsState = MusicDrumsState.MUSIC_DRUMS_OFF;
                }
                break;
            case MusicDrumsState.MUSIC_DRUMS_OFF:
                break;
            default:
                break;
        }

        if (aBurstScheme == 1) {
            setupVolumeForTune(mCurMusicTune, aDrumsVolume, aFadeTrackVolume);
        } else if (aBurstScheme == 2) {
            mMusicInterface.setSongVolume(mCurMusicFileMain, aMainTrackVolume);
            mMusicInterface.setSongVolume(mCurMusicFileDrums, aDrumsVolume);
            if (aDrumsJumpOrder != -1) {
                // Upstream Mix_ModMusicStreamJumpToOrder(GetMusicHandle(MUSIC_FILE_DRUMS), order)
                // (Music.cpp:558); the id-based playMusic call is the Java analogue of the jump.
                mMusicInterface.playMusic(mCurMusicFileDrums, aDrumsJumpOrder, false);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Level start selection + pause
    // ------------------------------------------------------------------------------------------

    /**
     * Testability seam for {@code Music::StartGameMusic}'s decision tree (Music.cpp:591-622): the
     * pure tune-selection table, with every caller-side predicate passed in.
     *
     * <p>Returns a {@link MusicTune} value, or {@link #MUSIC_TUNE_STOP} for the stormy-night branch
     * (upstream calls {@code StopAllMusic()}). {@code theLevel} is the adventure level (only
     * meaningful when the game is in adventure mode; the caller passes {@code -1} otherwise).
     */
    public static int selectGameTune(int theGameMode, int theLevel, boolean isFinalBossLevel,
            boolean isWallnutBowlingLevel, boolean isWhackAZombieLevel, boolean isLittleTroubleLevel,
            boolean isBungeeBlitzLevel, boolean isStormyNightLevel, boolean isScaryPotterLevel,
            boolean isIZombieLevel, boolean stageIsNight, boolean stageHasPool, boolean stageHas6Rows,
            boolean stageHasRoof) {
        if (theGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN || theGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            return MusicTune.MUSIC_TUNE_ZEN_GARDEN;
        } else if (isFinalBossLevel) {
            return MusicTune.MUSIC_TUNE_FINAL_BOSS_BRAINIAC_MANIAC;
        } else if (isWallnutBowlingLevel || isWhackAZombieLevel || isLittleTroubleLevel || isBungeeBlitzLevel
                || theGameMode == GameMode.GAMEMODE_CHALLENGE_SPEED) {
            return MusicTune.MUSIC_TUNE_MINIGAME_LOONBOON;
        } else if (theLevel == 10 || theLevel == 20 || theLevel == 30
                || theGameMode == GameMode.GAMEMODE_CHALLENGE_COLUMN) {
            return MusicTune.MUSIC_TUNE_CONVEYER;
        } else if (isStormyNightLevel) {
            return MUSIC_TUNE_STOP;
        } else if (isScaryPotterLevel || isIZombieLevel) {
            return MusicTune.MUSIC_TUNE_PUZZLE_CEREBRAWL;
        } else if (stageIsNight) {
            return stageHasPool ? MusicTune.MUSIC_TUNE_FOG_RIGORMORMIST : MusicTune.MUSIC_TUNE_NIGHT_MOONGRAINS;
        } else if (stageHas6Rows) {
            return MusicTune.MUSIC_TUNE_POOL_WATERYGRAVES;
        } else if (stageHasRoof) {
            return MusicTune.MUSIC_TUNE_ROOF_GRAZETHEROOF;
        } else {
            return MusicTune.MUSIC_TUNE_DAY_GRASSWALK;
        }
    }

    /**
     * Ported from src/Lawn/System/Music.cpp:591-622 ({@code Music::StartGameMusic}): gather the
     * board/game flags and dispatch to {@link #selectGameTune}; the stormy-night branch stops the
     * music, every other branch makes sure the selected tune plays.
     */
    public void startGameMusic() {
        int aLevel = (mApp != null && mApp.isAdventureMode()) ? mApp.mPlayerInfo.mLevel : -1;
        int aTune = selectGameTune(
                mApp.mGameMode,
                aLevel,
                mApp.isFinalBossLevel(),
                mApp.isWallnutBowlingLevel(),
                mApp.isWhackAZombieLevel(),
                mApp.isLittleTroubleLevel(),
                mApp.isBungeeBlitzLevel(),
                mApp.isStormyNightLevel(),
                mApp.isScaryPotterLevel(),
                mApp.isIZombieLevel(),
                mApp.mBoard.stageIsNight(),
                mApp.mBoard.stageHasPool(),
                mApp.mBoard.stageHas6Rows(),
                mApp.mBoard.stageHasRoof());

        if (aTune == MUSIC_TUNE_STOP) {
            stopAllMusic();
        } else {
            makeSureMusicIsPlaying(aTune);
        }
    }

    /**
     * Ported from src/Lawn/System/Music.cpp:624-670 ({@code Music::GameMusicPause}): entering pause
     * stores the current orders and pauses both streams; leaving pause resumes them, or re-issues
     * {@link #playMusic} when the main stream had actually stopped. The {@code Mix_PlayingMusicStream}
     * check maps to {@link MusicInterface#isPlaying}.
     */
    public void gameMusicPause(boolean thePause) {
        if (thePause) {
            if (!mPaused && mCurMusicTune != MusicTune.MUSIC_TUNE_NONE) {
                if (mCurMusicFileMain != MusicFile.MUSIC_FILE_NONE
                        && mCurMusicTune != MusicTune.MUSIC_TUNE_CREDITS_ZOMBIES_ON_YOUR_LAWN) {
                    mPauseOffset = getMusicOrder(mCurMusicFileMain);
                }
                if (mCurMusicTune == MusicTune.MUSIC_TUNE_NIGHT_MOONGRAINS
                        && mCurMusicFileDrums != MusicFile.MUSIC_FILE_NONE) {
                    mPauseOffsetDrums = getMusicOrder(mCurMusicFileDrums);
                }

                if (mCurMusicFileMain != MusicFile.MUSIC_FILE_NONE) {
                    mMusicInterface.pauseMusic(mCurMusicFileMain);
                }
                if (mCurMusicFileDrums != MusicFile.MUSIC_FILE_NONE) {
                    mMusicInterface.pauseMusic(mCurMusicFileDrums);
                }

                mPaused = true;
            }
        } else if (mPaused) {
            if (mCurMusicTune != MusicTune.MUSIC_TUNE_NONE) {
                boolean aMainPlaying = (mCurMusicFileMain != MusicFile.MUSIC_FILE_NONE)
                        && mMusicInterface.isPlaying(mCurMusicFileMain);
                if (mCurMusicTune == MusicTune.MUSIC_TUNE_CREDITS_ZOMBIES_ON_YOUR_LAWN || aMainPlaying) {
                    if (mCurMusicFileMain != MusicFile.MUSIC_FILE_NONE) {
                        mMusicInterface.resumeMusic(mCurMusicFileMain);
                    }
                    if (mCurMusicFileDrums != MusicFile.MUSIC_FILE_NONE) {
                        mMusicInterface.resumeMusic(mCurMusicFileDrums);
                    }
                } else {
                    playMusic(mCurMusicTune, mPauseOffset, mPauseOffsetDrums);
                }
            }
            mPaused = false;
        }
    }
}
