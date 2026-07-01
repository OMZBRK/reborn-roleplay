-- CreateTable
CREATE TABLE "Shot" (
    "id" TEXT NOT NULL,
    "authorId" TEXT NOT NULL,
    "filename" TEXT NOT NULL,
    "caption" TEXT,
    "width" INTEGER,
    "height" INTEGER,
    "likeCount" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Shot_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ShotLike" (
    "shotId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ShotLike_pkey" PRIMARY KEY ("shotId","userId")
);

-- CreateIndex
CREATE INDEX "Shot_createdAt_idx" ON "Shot"("createdAt");

-- CreateIndex
CREATE INDEX "Shot_authorId_idx" ON "Shot"("authorId");

-- AddForeignKey
ALTER TABLE "Shot" ADD CONSTRAINT "Shot_authorId_fkey" FOREIGN KEY ("authorId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ShotLike" ADD CONSTRAINT "ShotLike_shotId_fkey" FOREIGN KEY ("shotId") REFERENCES "Shot"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ShotLike" ADD CONSTRAINT "ShotLike_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
