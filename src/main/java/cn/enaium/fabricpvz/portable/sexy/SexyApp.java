package cn.enaium.fabricpvz.portable.sexy;

import cn.enaium.fabricpvz.portable.sexy.parsers.PropertiesParser;

/**
 * Ported from src/SexyAppFramework/SexyApp.h:34 (implementation
 * src/SexyAppFramework/SexyApp.cpp:39).
 *
 * <p>Product-level application class: demo prefix, product/company identification, the
 * {@code -version}/{@code -license} command line switches and the {@code partner.xml} property
 * overrides. Public fields keep their upstream names.
 */
public class SexyApp extends SexyAppBase {
    /** Upstream {@code gSexyApp} (SexyApp.cpp:51). */
    public static SexyApp gSexyApp;

    private static final String CLI_LICENSE_SUMMARY = "License: LGPL-3.0-or-later";

    private static final String CLI_LICENSE_NOTICE = """
            PvZ-Portable

            Copyright (C) 2026 Enaium

            SPDX-License-Identifier: LGPL-3.0-or-later
            Some source files also include:
            SPDX-License-Identifier: LGPL-3.0-or-later AND LicenseRef-PopCap

            PvZ-Portable is distributed under the GNU Lesser General Public
            License v3.0 or later. It is distributed in the hope that it will
            be useful, but WITHOUT ANY WARRANTY; without even the implied
            warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

            See LICENSE and COPYING for full terms.
            """;

    public int mBuildNum;
    public String mCommitDate = "";

    public String mUserName = "";

    // SexyApp::SexyApp (SexyApp.cpp:59)
    public SexyApp() {
        gSexyApp = this;

        mDemoPrefix = "pvzp";
        mDemoFileName = mDemoPrefix + ".dmo";
        mCompanyName = "Community";

        mBuildNum = 0;
    }

    // SexyApp::~SexyApp (SexyApp.cpp:70) — no Java counterpart; see SexyAppBase.dispose().
    public void dispose() {
        super.dispose();
    }

    // SexyApp::ReadFromRegistry (SexyApp.cpp:74)
    @Override
    public void readFromRegistry() {
        super.readFromRegistry();
    }

    // SexyApp::WriteToRegistry (SexyApp.cpp:79)
    @Override
    public void writeToRegistry() {
        super.writeToRegistry();
    }

    // SexyApp::HandleCmdLineParam (SexyApp.cpp:84)
    @Override
    public void handleCmdLineParam(String theParamName, String theParamValue) {
        if (theParamName.equals("-version")) {
            // Just print version info and then quit
            String aVersionString = "Product: " + mProdName + "\n"
                    + "Version: " + mProductVersion + "\n"
                    + "Build Num: " + mBuildNum + "\n"
                    + "Commit Date: " + mCommitDate + "\n"
                    + CLI_LICENSE_SUMMARY;

            // PORT-NOTE: the version/license dump is the game's own output like every other line it
            // prints, so it goes through the same log channel (`-version` and `-license` are the
            // only command line parameters this class handles, and both exit right after). The text
            // is unchanged; it now carries the workspace's log prefix, which is what a launcher
            // capturing the boot shows for every other line too.
            Common.logInfoLn("{}", aVersionString);
            doExit(0);
        } else if (theParamName.equals("-license") || theParamName.equals("-copyright")) {
            Common.logInfoLn("{}", CLI_LICENSE_NOTICE);
            doExit(0);
        } else {
            super.handleCmdLineParam(theParamName, theParamValue);
        }
    }

    // SexyApp::GetGameSEHInfo (SexyApp.cpp:130)
    @Override
    public String getGameSEHInfo() {
        return super.getGameSEHInfo()
                + "Build Num: " + mBuildNum + "\r\n"
                + "Commit Date: " + mCommitDate + "\r\n";
    }

    // SexyApp::PreDisplayHook (SexyApp.cpp:139)
    @Override
    public void preDisplayHook() {
    }

    // SexyApp::InitPropertiesHook (SexyApp.cpp:143)
    @Override
    public void initPropertiesHook() {
        // Load properties if we need to
        boolean checkSig = !isScreenSaver();
        loadProperties("properties/partner.xml", false, checkSig);

        // PORT-NOTE: the product name is this port's identity (`cn.enaium.fabricpvz`), so it is
        // deliberately not taken from the game data's properties/partner.xml, which names the retail
        // product - the same file still provides the window title.

        mIsWindowed = getBoolean("DefaultWindowed", mIsWindowed);

        String aNewTitle = getString("Title", "");
        if (!aNewTitle.isEmpty()) {
            mTitle = aNewTitle + " " + mProductVersion;
        }
    }

    // SexyApp::Init (SexyApp.cpp:155)
    @Override
    public void init() {
        Common.logInfoLn("Product: {}", mProdName);
        Common.logInfoLn("BuildNum: {}", mBuildNum);
        Common.logInfoLn("CommitDate: {}", mCommitDate);

        super.init();
    }

    // SexyApp::PreTerminate (SexyApp.cpp:164)
    @Override
    public void preTerminate() {
    }

    // SexyApp::UpdateFrames (SexyApp.cpp:168)
    @Override
    public void updateFrames() {
        super.updateFrames();
    }

    /** Kept for API completeness with the upstream properties parser (see {@link PropertiesParser}). */
    public static String licenseSummary() {
        return CLI_LICENSE_SUMMARY;
    }
}
