import type {Verse} from "@/types/main";
import {fetchData} from "@/lib/utils.ts";

export async function fetchVerses(translation: string, bookId: number, chapterId: number): Promise<Verse[]> {
  const res = await fetchData(`http://localhost:9090/api/${translation.toLowerCase()}/books/${bookId}/chapters/${chapterId}/verses`,
    "GET"
  );
  if (res.ok) {
    return await res.json();

  }
  throw new Error(`Failed to fetch books: ${res.status}`);
}
