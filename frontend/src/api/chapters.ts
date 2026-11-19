import {fetchData} from "@/lib/utils.ts";
import {BACKEND_BASE_URL} from "@/lib/constants.ts";

export const fetchChapterCount = async (bookId: number): Promise<{ count: number }> => {
  const response = await fetchData(`${BACKEND_BASE_URL}api/kjv/books/${bookId}/chapters/count`);
  if (!response.ok) {
    throw new Error('Failed to fetch chapter count');
  }
  return await response.json();
}
