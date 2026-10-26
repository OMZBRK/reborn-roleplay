import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { AssignmentModule } from '../admin/assignment.module';
import { OralSlotsModule } from '../oral-slots/oral-slots.module';
import { WhitelistController } from './whitelist.controller';
import { WhitelistService } from './whitelist.service';
import { WhitelistMessagesService } from './whitelist-messages.service';
import { WhitelistVoixService } from './whitelist-voix.service';

@Module({
  imports: [AuthModule, AssignmentModule, OralSlotsModule],
  controllers: [WhitelistController],
  providers: [WhitelistService, WhitelistMessagesService, WhitelistVoixService],
  // Exportés : StaffModule relaie les messages staff (postStaffMessage) ;
  // AdminModule sert l'écoute staff de la voix (GET /admin/whitelist/:id/voix).
  exports: [WhitelistMessagesService, WhitelistVoixService],
})
export class WhitelistModule {}
