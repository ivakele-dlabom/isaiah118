import CrossReferences from "../references/CrossReferences";
import {useState} from "react";
import Comments from "@/components/main/comments/Comments.tsx";

interface Props {
}
export const VerseFeed = ({}: Props) => {
    const [selectedFeed, setSelectedFeed] = useState<"comments" | "cross-references">("cross-references");
    return (
        <section className="w-full bg-white ">
            <div className={"flex flex-row items-center mx-auto w-fit"}>
                <button onClick={() => setSelectedFeed("comments")} className={`hover:cursor-pointer font-bold p-2 font-geist-mono ${selectedFeed !== "comments" ? "text-gray-300": ""}`}>Comments</button>
                <div className="w-px h-4 bg-gray-300 mx-4"></div>
                <button onClick={() => setSelectedFeed("cross-references")} className={`hover:cursor-pointer  p-2 font-geist-mono font-bold ${selectedFeed !== "cross-references"? "text-gray-500" : ""}`}>Cross references</button>
            </div>
            <div className="w-full h-px bg-gray-300 my-4"></div>
            { selectedFeed === "cross-references" ? <CrossReferences /> : <Comments/> }


        </section>
    )
}
