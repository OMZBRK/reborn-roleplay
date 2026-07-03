import { Module } from '@nestjs/common';
import { MenuController } from './menu.controller';
import { MenuService } from './menu.service';

// Pas d'AuthModule : route publique. PrismaService vient du PrismaModule global.
@Module({
  controllers: [MenuController],
  providers: [MenuService],
})
export class MenuModule {}
