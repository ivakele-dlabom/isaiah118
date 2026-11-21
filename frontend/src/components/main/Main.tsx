import { BookDisplay } from "./BookDisplay.tsx";
import { useBookNavigation } from "@/contexts/BookContext.tsx";
import {Profile} from "@/components/Profile.tsx";
import AppIcon from "@/assets/app-icon-v2.svg";


const Main = () => {
  const { translation, setTranslation } = useBookNavigation();
  const availableTranslation = ["KJV"];

  return (
    <section className="font-firacode grid grid-cols-1 gap-4  min-h-screen px-6">
      {/*
        Select translation version dropdown
        */}
      <label className="absolute z-10 inline-flex right-20 top-5 hover:cursor-pointer">
        <span className="sr-only">Bible translation</span>
        <select
          value={translation}
          onChange={(e) => setTranslation(e.target.value)}
          className="h-8 rounded-sm border border-gray-300 px-6 font-bold text-xl"
        >
          {availableTranslation .map((v) => (
            <option key={v} value={v} className="uppercase">
              {v}
            </option>
          ))}
        </select>
      </label>
        <div className={"absolute z-10 inline-flex left-4 top-4  hover:cursor-pointer"}>
            <img className={"size-8"} src={AppIcon}  alt={"App icon"}/>
        </div>
        <Profile/>
        
      <BookDisplay className="w-full" />
    </section>
  );
};

export default Main;
