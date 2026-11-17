import {MessageSquare} from "lucide-react";

export default function Comments() {
    return (
        <section
            aria-labelledby="comments-heading"
            className="flex flex-col items-center justify-center gap-3 rounded-xl font-geist-mono border border-dashed border-gray-300 bg-gray-50 px-8 py-12 text-center dark:border-gray-700 dark:bg-gray-900"
        >
            <div className="rounded-full bg-gray-200 p-3 dark:bg-gray-800">
                <MessageSquare className="h-6 w-6 text-gray-500" aria-hidden="true" />
            </div>

            <h2 id="comments-heading" className="text-lg font-semibold font-geist-mono">
                Comments are coming soon
            </h2>

            <p className="max-w-sm text-sm text-gray-500 font-geist-mono">
                We're building a space for you to discuss and share thoughts on each
                verse. Check back soon.
            </p>
        </section>
    )
}