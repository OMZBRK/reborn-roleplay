import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { ManifestController } from './manifest.controller';
import { ManifestService } from './manifest.service';

@Module({
  imports: [AuthModule],
  controllers: [ManifestController],
  providers: [ManifestService],
})
export class ManifestModule {}
