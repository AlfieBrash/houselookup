export const DEV_MODE_STORAGE_KEY = "os-places-stub-enabled";

export function isDeveloperModeEnabled(): boolean {
  if (typeof window === "undefined") {
    return false;
  }

  return window.localStorage.getItem(DEV_MODE_STORAGE_KEY) === "true";
}
