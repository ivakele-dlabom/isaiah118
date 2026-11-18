import type { CrossReference } from "@/types/main";
import {fetchData} from "@/lib/utils.ts";

export async function fetchCrossReferences(
  translation: string,
  bookName: string,
  chapterNumber: number,
  verse: number,
): Promise<CrossReference[]> {
  const res = await fetchData(
    `http://localhost:9090/api/${translation.toLocaleLowerCase()}/cross-references/${bookName}/${chapterNumber}/${verse}`, "GET"
  );

  if (res.ok) {
    return await res.json();
  }

  throw new Error(`Failed to fetch cross references: ${res.status}`);
}