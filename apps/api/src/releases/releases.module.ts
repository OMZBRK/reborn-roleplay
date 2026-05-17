import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import {
  AdminReleasesController,
  LauncherUpdateController,
} from './releases.controller';
import { ReleasesService } from './releases.service';

@Module({
  imports: [AuthModule],
  controllers: [LauncherUpdateController, AdminReleasesController],
  providers: [ReleasesService],
})
export class ReleasesModule {}
