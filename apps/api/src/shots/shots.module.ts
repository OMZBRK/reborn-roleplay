import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { ShotsController } from './shots.controller';
import { ShotsService } from './shots.service';

@Module({
  imports: [AuthModule],
  controllers: [ShotsController],
  providers: [ShotsService],
})
export class ShotsModule {}
