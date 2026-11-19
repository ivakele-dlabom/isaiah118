import type { Book } from "@/types/main";
import {fetchData} from "@/lib/utils.ts";
import {BACKEND_BASE_URL} from "@/lib/constants.ts";

export async function fetchBooks(translation: string): Promise<Book[]> {
  const res = await fetchData(`${BACKEND_BASE_URL}api/${translation.toLowerCase()}/books`, "GET");

  if (res.ok) {

    const data = await res.json();

    return data.map((book: { id: number; name: string }) => ({
      id: book.id,
      name: book.name,
      chapters: [],
    }));
  }

  throw new Error(`Failed to fetch books: ${res.status}`);
}
