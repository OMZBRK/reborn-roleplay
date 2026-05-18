import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import {
  AdminManifestController,
  ManifestController,
} from './manifest.controller';
import { ManifestService } from './manifest.service';

@Module({
  imports: [AuthModule],
  controllers: [ManifestController, AdminManifestController],
  providers: [ManifestService],
})
export class ManifestModule {}
