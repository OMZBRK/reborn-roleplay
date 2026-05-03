import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { LoreController } from './lore.controller';
import { LoreService } from './lore.service';

@Module({
  imports: [AuthModule],
  controllers: [LoreController],
  providers: [LoreService],
})
export class LoreModule {}
