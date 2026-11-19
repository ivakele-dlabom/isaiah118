import type {Verse} from "@/types/main";
import {fetchData} from "@/lib/utils.ts";
import {BACKEND_BASE_URL} from "@/lib/constants.ts";

export async function fetchVerses(translation: string, bookId: number, chapterId: number): Promise<Verse[]> {
  const res = await fetchData(`${BACKEND_BASE_URL}api/${translation.toLowerCase()}/books/${bookId}/chapters/${chapterId}/verses`,
    "GET"
  );
  if (res.ok) {
    return await res.json();

  }
  throw new Error(`Failed to fetch books: ${res.status}`);
}
