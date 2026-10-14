import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { StaffMediaService } from './staff-media.service';
import {
  StaffMediaPublicController,
  StaffMessagesController,
} from './staff-messages.controller';
import { StaffMessagesService } from './staff-messages.service';

/**
 * Annonces / dev updates du Discord staff rédigées dans le panel.
 * AuditModule et WebhooksModule sont @Global (pas d'import).
 */
@Module({
  imports: [AuthModule],
  controllers: [StaffMessagesController, StaffMediaPublicController],
  providers: [StaffMessagesService, StaffMediaService],
})
export class StaffMessagesModule {}
