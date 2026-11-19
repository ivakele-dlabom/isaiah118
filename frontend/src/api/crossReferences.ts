import type { CrossReference } from "@/types/main";
import {fetchData} from "@/lib/utils.ts";
import {BACKEND_BASE_URL} from "@/lib/constants.ts";

export async function fetchCrossReferences(
  translation: string,
  bookName: string,
  chapterNumber: number,
  verse: number,
): Promise<CrossReference[]> {
  const res = await fetchData(
    `${BACKEND_BASE_URL}api/${translation.toLocaleLowerCase()}/cross-references/${bookName}/${chapterNumber}/${verse}`, "GET"
  );

  if (res.ok) {
    return await res.json();
  }

  throw new Error(`Failed to fetch cross references: ${res.status}`);
}