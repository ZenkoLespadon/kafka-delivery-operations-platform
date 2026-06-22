import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
    title: "Delivery Operations Dashboard",
    description: "Real-time delivery monitoring dashboard"
};

export default function RootLayout({
                                       children
                                   }: Readonly<{
    children: React.ReactNode;
}>) {
    return (
        <html lang="en">
        <body>{children}</body>
        </html>
    );
}