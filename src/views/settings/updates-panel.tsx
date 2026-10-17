import { useT } from "@/lib/i18n";
import { ExperimentalChangelog } from "@/components/update/experimental-changelog";
import { isAndroid, isLinuxDesktop } from "@/lib/platform";
import { openUrl } from "@/lib/window";
import { Section } from "./shared";
import { BetaChannelRow, UpdatesRow } from "./advanced-panel";
import { RollbackRow } from "./rollback-row";
import { BuildFeedback } from "./build-feedback";
import { BackupRow } from "./backup-row";
import { SettingsRecoverRow } from "./settings-recover-row";
import { ExperimentalBuildsSection } from "./experimental-builds-section";
import { readBetaReturnContext } from "@/lib/updater/beta-return";
import { useExperimentalAccess } from "@/lib/updater/experimental-access";

const isTauri = typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;
const RELEASES_URL = "https://github.com/9vsv6/lart/releases";

export function UpdatesPanel() {
  const t = useT();
  const supportsInAppUpdates = isTauri && !isLinuxDesktop() && !isAndroid();
  const experimentalInstalled = readBetaReturnContext(__APP_VERSION__) !== null;
  const experimentalAccess = useExperimentalAccess();

  return (
    <>
      <ExperimentalChangelog />
      {supportsInAppUpdates && (
        <Section
          title={t("Updates")}
          subtitle={t(
            "Lart checks harbor.site for new versions and installs them in place. Nothing installs until you choose to, and a dismissed update never nags you again.",
          )}
        >
          <UpdatesRow />
          <BetaChannelRow />
          {!experimentalInstalled && <RollbackRow />}
          <BuildFeedback />
        </Section>
      )}

      {isAndroid() && (
        // No in-app updater on Android: new APKs are published as releases.
        <Section
          title={t("Updates")}
          subtitle={t("Lart for Android updates by installing the newest APK from the releases page.")}
        >
          <button
            type="button"
            onClick={() => openUrl(RELEASES_URL)}
            className="self-start rounded-md bg-elevated px-4 py-2.5 text-[15px] font-medium text-ink"
          >
            {t("Open releases")}
          </button>
        </Section>
      )}

      {(experimentalAccess || experimentalInstalled) && <ExperimentalBuildsSection />}

      <Section
        title={t("Backup & restore")}
        subtitle={t(
          "Export your entire Lart setup to a single file, then restore it on a new computer or keep it as a backup. Everything is included except your Stremio sign-in.",
        )}
      >
        <SettingsRecoverRow />
        <BackupRow />
      </Section>
    </>
  );
}
