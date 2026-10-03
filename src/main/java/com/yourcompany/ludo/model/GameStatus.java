package com.yourcompany.ludo.model;

public enum GameStatus {
    MATCH_FOUND,  // ম্যাচ পাওয়া গেছে, কাউন্টডাউন চলছে
    ONGOING,      // খেলা চলছে
    FINISHED,     // শেষ (বিজয়ী নির্ধারিত)
    CANCELLED     // বাতিল (ফি কাটা হলে রিফান্ড হয়েছে)
}
